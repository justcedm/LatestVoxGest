package com.voxgest.dryrun
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs

class Core5Runtime(private val context:Context, val variant: Core5Variant = Core5Variant.BASELINE):AutoCloseable {
    val root=variant.assetRoot
    val manifest=JSONObject(context.assets.open("$root/runtime_manifest.json").bufferedReader().use { it.readText() })
    val profile=variant.profileId
    val modelHash=manifest.getString("model_sha256")
    private val interpreter:Interpreter
    init {
        check(BuildConfig.DEBUG)
        require(manifest.getString("profile")==profile)
        require(manifest.getString("feature_version")==Core5Contract.VERSION)
        require(!manifest.getBoolean("analysis_mirrored"))
        require(manifest.getString("temporal")=="observed_hand_envelope_context100ms_timestamp48")
        require(manifest.getInt("sequence_length")==48)
        require(manifest.getBoolean("experimental") && !manifest.getBoolean("production_default_changed"))
        val labels=manifest.getJSONArray("labels")
        require(List(labels.length()) { labels.getString(it) }==Core5Contract.labels)
        val model=context.assets.open("$root/core5_float32.tflite").use { it.readBytes() }
        require(hash(model)==modelHash)
        val labelsBytes=context.assets.open("$root/labels.json").use { it.readBytes() }
        require(hash(labelsBytes)==manifest.getString("labels_sha256"))
        val labelObject=JSONObject(labelsBytes.toString(Charsets.UTF_8))
        require(labelObject.getString("profile")==profile)
        val labelArray=labelObject.getJSONArray("labels")
        require(List(labelArray.length()) { labelArray.getString(it) }==Core5Contract.labels)
        val tasks=manifest.getJSONObject("tasks_assets")
        tasks.keys().forEach { name -> require(hash(context.assets.open("model/$name").use { it.readBytes() })==tasks.getString(name)) }
        val buffer=ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder()).put(model); buffer.rewind()
        interpreter=Interpreter(buffer,Interpreter.Options().setNumThreads(2)); interpreter.allocateTensors()
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1,48,225)))
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1,5)))
        require(interpreter.getInputTensor(0).dataType()==DataType.FLOAT32)
        require(interpreter.getOutputTensor(0).dataType()==DataType.FLOAT32)
    }
    fun infer(tensor:Array<FloatArray>):FloatArray {
        require(tensor.size==48 && tensor.all { it.size==225 && it.all(Float::isFinite) })
        val out=Array(1) { FloatArray(5) }; interpreter.run(arrayOf(tensor),out)
        require(out[0].all(Float::isFinite)); return out[0]
    }
    fun parity():JSONObject {
        val feature=StandardFslParityHarness(context).runFeatureParity()
        check(feature.status==StandardFslParityStatus.PASS) { feature.evidence }
        val raw=context.assets.open("$root/golden_tensor.bin").use { it.readBytes() }
        check(hash(raw)==manifest.getString("golden_sha256"))
        val b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val tensor=Array(48) { FloatArray(225) { b.float } }
        val expected=JSONObject(context.assets.open("$root/golden_expected.json").bufferedReader().use { it.readText() }).getJSONArray("probabilities")
        val actual=infer(tensor); val delta=actual.indices.maxOf { abs(actual[it]-expected.getDouble(it).toFloat()) }
        check(delta<=1e-5f)
        if (variant == Core5Variant.SPARSE10FPS_CANDIDATE) {
            check(actual.indices.maxByOrNull { actual[it] } == 0) { "Supplied golden top-1 is not HELLO" }
        }
        val temporal=listOf(FloatArray(225),FloatArray(225),FloatArray(225)); temporal[1][10]=10f; temporal[2][10]=100f
        check(abs(Core5Contract.resample(temporal,listOf(0,10,100),3)[1][10]-50f)<1e-5f)
        return JSONObject().put("profile",profile).put("variant",variant.intentValue)
            .put("feature_parity","PASS").put("temporal_parity","PASS")
            .put("tflite_parity","PASS").put("max_probability_difference",delta)
            .put("golden_top1",Core5Contract.labels[actual.indices.maxByOrNull { actual[it] }!!])
            .put("model_sha256",modelHash).put("input","[1,48,225]").put("output","[1,5]")
    }
    override fun close()=interpreter.close()
    companion object {
        fun hash(bytes:ByteArray):String=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        fun tensorHash(tensor:Array<FloatArray>):String {
            val b=ByteBuffer.allocate(48*225*4).order(ByteOrder.LITTLE_ENDIAN)
            tensor.forEach { row -> row.forEach { b.putFloat(it) } }; return hash(b.array())
        }
        fun array(values:FloatArray)=JSONArray().apply { values.forEach { put(it.toDouble()) } }
    }
}
