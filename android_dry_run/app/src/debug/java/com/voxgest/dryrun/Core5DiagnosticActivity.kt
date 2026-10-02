package com.voxgest.dryrun
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/** Explicit debug entry point. Production navigation and profiles are untouched. */
class Core5DiagnosticActivity:ComponentActivity() {
    private val executor=Executors.newSingleThreadExecutor()
    private val sensor=AtomicLong(); private val analyzed=AtomicLong()
    private val timer=Handler(Looper.getMainLooper())
    private lateinit var preview:PreviewView; private lateinit var status:TextView
    private lateinit var labels:Spinner; private lateinit var modes:Spinner
    private var runtime:Core5Runtime?=null; private var extractor:MediaPipeLandmarkExtractor?=null
    private var recorder:Core5Recorder?=null; private var provider:ProcessCameraProvider?=null
    private var metrics:LandmarkExtractionMetrics?=null
    private val controlledCountdownMs=2000L
    private val controlledWindowMs=4500L
    @Volatile private var controlledPhase="READY"
    @Volatile private var controlledLabel=""
    private val timeout=Runnable { executor.execute { recorder?.finish("EVENT_TIMEOUT") } }
    private val controlledFinish=Runnable {
        if(controlledPhase=="SIGN_NOW") {
            controlledPhase="RECOGNIZING"; show("RECOGNIZING / CONTROLLED_WINDOW")
            executor.execute { recorder?.finish("CONTROLLED_WINDOW_END") }
        }
    }
    private val controlledStart=Runnable {
        executor.execute {
            try {
                check(controlledPhase=="GET_READY")
                checkNotNull(recorder) { "Startup parity not ready" }
                    .begin(controlledLabel,"CONTROLLED_WINDOW",sensor.get(),analyzed.get())
                controlledPhase="SIGN_NOW"
                show("SIGN NOW / one complete sign / ${controlledWindowMs}ms")
                timer.postDelayed(controlledFinish,controlledWindowMs)
            } catch(e:Exception) { controlledPhase="READY"; show("BEGIN_BLOCKED "+e.message) }
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); check(BuildConfig.DEBUG)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        status=TextView(this).apply { text="Core5 initializing"; textSize=16f }; box.addView(status)
        labels=Spinner(this).apply { adapter=ArrayAdapter(this@Core5DiagnosticActivity,
            android.R.layout.simple_spinner_dropdown_item,Core5Contract.labels+listOf("NON_SIGN:neutral",
            "NON_SIGN:open_palm","NON_SIGN:wave","NON_SIGN:partial","NON_SIGN:entry_exit","NON_SIGN:body")) }; box.addView(labels)
        modes=Spinner(this).apply { adapter=ArrayAdapter(this@Core5DiagnosticActivity,
            android.R.layout.simple_spinner_dropdown_item,listOf("MANUAL","AUTO_LEGACY","AUTO_MOTION","CONTROLLED_WINDOW")) }; box.addView(modes)
        box.addView(Button(this).apply { text="START SIGNING / BEGIN ONE ATTEMPT"; setOnClickListener { begin(labels.selectedItem.toString(),modes.selectedItem.toString()) } })
        box.addView(Button(this).apply { text="END / SAVE MANUAL ATTEMPT"; setOnClickListener { end() } })
        preview=PreviewView(this).apply { scaleType=PreviewView.ScaleType.FIT_CENTER }
        box.addView(preview,LinearLayout.LayoutParams(-1,0,1f)); setContentView(box)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) initialize()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA),501)
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,results:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if(requestCode==501 && results.firstOrNull()==PackageManager.PERMISSION_GRANTED) initialize()
    }
    private fun show(value:String) { Log.i("Core5",value); runOnUiThread { status.text=value } }
    private fun initialize() {
        executor.execute {
            try {
                val r=Core5Runtime(this,Core5Variant.fromIntent(intent.getStringExtra("core5_variant"))); runtime=r
                val parity=r.parity(); val directory=File(filesDir,"core5_diagnostics"); directory.mkdirs()
                File(directory,"startup-"+System.currentTimeMillis()+".json").writeText(
                    parity.put("profile",r.profile).put("device",android.os.Build.MODEL)
                        .put("android",android.os.Build.VERSION.RELEASE).put("analysis_mirrored",false)
                        .put("anatomy_physical_verification","PENDING").toString())
                recorder=Core5Recorder(directory,r) { message ->
                    timer.removeCallbacks(timeout); timer.removeCallbacks(controlledFinish)
                    controlledPhase="RESULT"; show(message)
                }
                extractor=MediaPipeLandmarkExtractor(this,false,practical15AnatomicalSlots=true,onMetrics={metrics=it})
                show(if (r.variant == Core5Variant.BASELINE)
                    "STARTUP_PARITY_PASS Core5 [1,48,225] -> [1,5]; physical anatomy pending"
                else "STARTUP_PARITY_PASS "+r.profile+" [1,48,225] -> [1,5]; physical anatomy pending")
                runOnUiThread { bind() }
            } catch(e:Exception) { show("STARTUP_BLOCKED "+e.javaClass.simpleName+": "+e.message) }
        }
    }
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun bind() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val p=future.get(); provider=p; val pb=Preview.Builder()
                Camera2Interop.Extender(pb).setSessionCaptureCallback(object:CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session:CameraCaptureSession,request:CaptureRequest,result:TotalCaptureResult) { sensor.incrementAndGet() }
                })
                val pv=pb.build(); pv.setSurfaceProvider(preview.surfaceProvider)
                val analysis=ImageAnalysis.Builder().setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(640,480)).build()
                analysis.setAnalyzer(executor) { image ->
                    analyzed.incrementAndGet()
                    try {
                        val f=extractor?.processFrame(image)?.let { Core5Contract.sanitize(it) }
                        if(f!=null) {
                            recorder?.frame(f,metrics,sensor.get(),analyzed.get(),image.imageInfo.rotationDegrees)
                            if(analyzed.get()%30L==0L && recorder?.active!=true && controlledPhase=="READY")
                                show("READY frames="+analyzed.get()+" pose="+f.hasPose+" L="+f.hasLeftHand+" R="+f.hasRightHand+" / "+f.handObservations.joinToString { it.mediaPipeHandedness })
                        }
                    } catch(e:Exception) {
                        recorder?.finish("CAPTURE_ERROR"); show("CAPTURE_ERROR "+e.javaClass.simpleName)
                    } finally { image.close() }
                }
                p.bindToLifecycle(this,CameraSelector.DEFAULT_FRONT_CAMERA,pv,analysis)
            } catch(e:Exception) { show("CAMERA_BLOCKED "+e.message) }
        },ContextCompat.getMainExecutor(this))
    }
    private fun begin(label:String,mode:String) {
        if(mode=="CONTROLLED_WINDOW") {
            if(controlledPhase !in setOf("READY","RESULT")) { show("BEGIN_BLOCKED capture already in progress"); return }
            if(runtime==null || recorder==null) { show("BEGIN_BLOCKED startup parity not ready"); return }
            controlledLabel=label
            controlledPhase="GET_READY"
            show("GET READY / hands down / ${controlledCountdownMs}ms countdown")
            timer.removeCallbacks(controlledStart); timer.postDelayed(controlledStart,controlledCountdownMs)
            return
        }
        executor.execute {
            try {
                checkNotNull(recorder) { "Startup parity not ready" }.begin(label,mode,sensor.get(),analyzed.get())
                timer.removeCallbacks(timeout); timer.postDelayed(timeout,8000); show("RECORDING "+label+" / "+mode)
            } catch(e:Exception) { show("BEGIN_BLOCKED "+e.message) }
        }
    }
    private fun end(reason:String="MANUAL_END") {
        if(controlledPhase=="GET_READY") {
            timer.removeCallbacks(controlledStart); controlledPhase="READY"; show("READY / countdown cancelled"); return
        }
        if(controlledPhase=="SIGN_NOW" && reason=="MANUAL_END") {
            show("SIGN NOW / fixed window ends automatically; END is disabled"); return
        }
        timer.removeCallbacks(controlledFinish)
        val timestamp=SystemClock.elapsedRealtime()
        executor.execute { recorder?.finish(reason,timestamp) }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        when(intent.getStringExtra("command")) {
            "begin" -> begin(intent.getStringExtra("expected")?:labels.selectedItem.toString(),intent.getStringExtra("mode")?:"MANUAL")
            "end" -> end()
            "cancel" -> end("OPERATOR_CANCEL")
            "replay" -> executor.execute {
                try {
                    val name=intent.getStringExtra("event")?:error("event required")
                    require(name.matches(Regex("[a-zA-Z0-9-]+\\.json")))
                    val directory=File(filesDir,"core5_diagnostics"); val payload=JSONObject(File(directory,name).readText())
                    require(payload.getString("model_sha256")==runtime!!.modelHash)
                    val raw=payload.getJSONArray("tensor")
                    val tensor=Array(48) { i -> FloatArray(225) { j -> raw.getJSONArray(i).getDouble(j).toFloat() } }
                    require(Core5Runtime.tensorHash(tensor)==payload.getString("tensor_sha256"))
                    val probabilities=runtime!!.infer(tensor)
                    val target=File(directory,name.removeSuffix(".json")+".android-replay-"+System.currentTimeMillis()+".json")
                    target.writeText(JSONObject().put("event_id",payload.getString("event_id"))
                        .put("model_sha256",runtime!!.modelHash).put("tensor_sha256",Core5Runtime.tensorHash(tensor))
                        .put("probabilities",Core5Runtime.array(probabilities)).toString())
                    show("EXACT_EVENT_REPLAY_SAVED "+target.name)
                } catch(e:Exception) { show("REPLAY_BLOCKED "+e.message) }
            }
        }
    }
    override fun onStop() {
        timer.removeCallbacks(timeout); timer.removeCallbacks(controlledStart)
        timer.removeCallbacks(controlledFinish); end("LIFECYCLE_STOP"); super.onStop()
    }
    override fun onDestroy() {
        provider?.unbindAll(); executor.execute { extractor?.close(); runtime?.close() }; executor.shutdown()
        super.onDestroy()
    }
}
