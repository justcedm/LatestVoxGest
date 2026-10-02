package com.voxgest.dryrun
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Diagnostic-only evidence. User-labelled attempts never become supervised FSL data. */
class Core5Recorder(private val output:File,private val runtime:Core5Runtime,private val done:(String)->Unit) {
    data class Sample(val frame:LandmarkFrame,val metrics:LandmarkExtractionMetrics?,val rotation:Int)
    private val samples=mutableListOf<Sample>()
    private var legacy=FslPractical15CompleteEventCollector()
    var active=false; private set
    private var expected=""; private var mode=""; private var id=""
    private var sensorStart=0L; private var analyzedStart=0L; private var commandStart=0L
    private var motionStarted:Long?=null; private var stillSince:Long?=null
    private var lastSensor=0L; private var lastAnalyzed=0L; private var processed=0
    fun begin(label:String,boundary:String,sensor:Long,analyzed:Long) {
        require(!active) { "Finish current event first" }
        require(label in Core5Contract.labels || label.startsWith("NON_SIGN:"))
        require(boundary in setOf("MANUAL","AUTO_LEGACY","AUTO_MOTION","CONTROLLED_WINDOW"))
        samples.clear(); legacy=FslPractical15CompleteEventCollector()
        expected=label; mode=boundary; id=UUID.randomUUID().toString()
        sensorStart=sensor; analyzedStart=analyzed; lastSensor=sensor; lastAnalyzed=analyzed; processed=0
        commandStart=SystemClock.elapsedRealtime(); motionStarted=null; stillSince=null; active=true
    }
    fun frame(f:LandmarkFrame,m:LandmarkExtractionMetrics?,sensor:Long,analyzed:Long,rotation:Int) {
        if(!active) return
        lastSensor=sensor; lastAnalyzed=analyzed; processed++
        samples.add(Sample(f,m,rotation))
        if(samples.size>=600) { finish("EVENT_TIMEOUT"); return }
        if(mode=="AUTO_LEGACY") {
            val update=legacy.onFrame(f)
            if(update.reason=="SIGN_ENTRY") { samples.clear(); samples.add(Sample(f,m,rotation)) }
            if(update.candidate!=null) finish("AUTO_LEGACY_RELEASE")
            else if(update.reason in setOf("EVENT_TIMEOUT","POSE_TRACKING_LOST","INCOMPLETE_EVENT_REJECTED")) finish(update.reason)
        } else if(mode=="AUTO_MOTION" && f.hasPose && f.hasAnyHand) {
            if(motionStarted==null) motionStarted=f.timestampMs
            val previous=samples.getOrNull(samples.lastIndex-1)?.frame
            if(previous!=null && previous.hasPose && previous.hasAnyHand) {
                val delta=Core5Contract.motion(listOf(StandardFullSign225FeatureBuilder.build(previous).vector,
                    StandardFullSign225FeatureBuilder.build(f).vector))
                // Separate experiment only: preserves a 900ms final hold; linguistic end is not claimed.
                if(delta<.08f) {
                    if(stillSince==null) stillSince=f.timestampMs
                    if(f.timestampMs-motionStarted!!>=1200 && f.timestampMs-stillSince!!>=900) finish("AUTO_MOTION_FINAL_HOLD_900MS")
                } else stillSince=null
            } else stillSince=null
        }
    }
    fun finish(reason:String="MANUAL_END",endCommandMs:Long=SystemClock.elapsedRealtime()) {
        if(!active) return
        active=false
        val termination=if(mode!="MANUAL" && reason=="MANUAL_END") "OPERATOR_CANCEL" else reason
        val saved=samples.toList(); val frames=saved.map { it.frame }
        val range=Core5Contract.envelope(frames); val included=range?.map { frames[it] }.orEmpty()
        val vectors=included.map { StandardFullSign225FeatureBuilder.build(it).vector }
        var tensor:Array<FloatArray>?=null; var probabilities:FloatArray?=null
        var latency:Double?=null; var error:String?=null
        if(included.size>=2) {
            try {
                tensor=Core5Contract.resample(vectors,included.map { it.timestampMs })
                val start=SystemClock.elapsedRealtimeNanos(); probabilities=runtime.infer(tensor)
                latency=(SystemClock.elapsedRealtimeNanos()-start)/1e6
            } catch(e:Exception) { error=e.javaClass.simpleName+":"+e.message }
        }
        val rejection=if(error!=null) "INFERENCE_ERROR" else if(probabilities==null) "NO_OBSERVED_TRAJECTORY"
            else Core5Contract.gate(probabilities,included,vectors,termination)
        val ranked=probabilities?.indices?.sortedByDescending { probabilities[it] }.orEmpty()
        fun points(p:List<LandmarkPoint>?,count:Int)=JSONArray().apply {
            repeat(count) { i -> val v=p?.getOrNull(i); put(JSONArray(listOf(v?.x?:0f,v?.y?:0f,v?.z?:0f))) }
        }
        val times=frames.map { it.timestampMs }; val gaps=times.zipWithNext { a,b -> b-a }
        val json=JSONObject().put("schema","core5_event_v1").put("profile",runtime.profile)
            .put("variant",runtime.variant.intentValue)
            .put("feature_version",Core5Contract.VERSION).put("model_sha256",runtime.modelHash)
            .put("event_id",id).put("expected_test_label",expected).put("boundary_mode",mode)
            .put("supervised_training_allowed",false).put("linguistic_ground_truth_validated",false)
            .put("termination",termination).put("gate_reason",rejection).put("accepted",rejection=="ACCEPTED")
            .put("capture_origin","ANDROID_CAMERA").put("device_model",android.os.Build.MODEL)
            .put("labels",JSONArray(Core5Contract.labels))
            .put("event_start_ms",times.firstOrNull()?:JSONObject.NULL).put("event_end_ms",times.lastOrNull()?:JSONObject.NULL)
            .put("end_command_ms",endCommandMs).put("operator_elapsed_ms",SystemClock.elapsedRealtime()-commandStart)
            .put("camera_frame_count",lastSensor-sensorStart).put("analyzer_delivered_count",lastAnalyzed-analyzedStart)
            .put("processed_result_count",processed).put("raw_frame_count",frames.size)
            .put("camera_count_note","Camera2 callbacks during operator attempt; difference from analyzer counts is approximate")
            .put("estimated_skipped_frames",(lastSensor-sensorStart-(lastAnalyzed-analyzedStart)).coerceAtLeast(0))
            .put("processed_fps",if(times.size>1 && times.last()>times.first()) (times.size-1)*1000.0/(times.last()-times.first()) else JSONObject.NULL)
            .put("max_gap_ms",gaps.maxOrNull()?:JSONObject.NULL)
            .put("envelope_frame_count",included.size).put("envelope_start_index",range?.first?:JSONObject.NULL)
            .put("envelope_end_index",range?.last?:JSONObject.NULL).put("trajectory_motion_mean_l2",Core5Contract.motion(vectors))
            .put("pose_ratio",if(included.isNotEmpty()) included.count{it.hasPose}.toDouble()/included.size else JSONObject.NULL)
            .put("left_ratio",if(included.isNotEmpty()) included.count{it.hasLeftHand}.toDouble()/included.size else JSONObject.NULL)
            .put("right_ratio",if(included.isNotEmpty()) included.count{it.hasRightHand}.toDouble()/included.size else JSONObject.NULL)
            .put("tflite_latency_ms",latency?:JSONObject.NULL)
            .put("end_command_to_result_ms",SystemClock.elapsedRealtime()-endCommandMs)
            .put("android_probabilities",probabilities?.let { Core5Runtime.array(it) }?:JSONObject.NULL)
            .put("raw_top1",ranked.firstOrNull()?.let { Core5Contract.labels[it] }?:JSONObject.NULL)
            .put("confidence",ranked.firstOrNull()?.let { probabilities!![it] }?:JSONObject.NULL)
            .put("margin",if(ranked.size>=2) probabilities!![ranked[0]]-probabilities!![ranked[1]] else JSONObject.NULL)
            .put("top5",JSONArray().apply { ranked.forEach { i -> put(JSONObject().put("label",Core5Contract.labels[i]).put("probability",probabilities!![i])) } })
            .put("tensor",tensor?.let { JSONArray().apply { it.forEach { row -> put(Core5Runtime.array(row)) } } }?:JSONObject.NULL)
            .put("tensor_sha256",tensor?.let { Core5Runtime.tensorHash(it) }?:JSONObject.NULL)
            .put("inference_error",error?:JSONObject.NULL).put("analysis_mirrored",false)
            .put("preview_mirrored",true).put("camera","FRONT").put("text_tts_emitted",false)
            .put("diagnostic_output_only",true)
            .put("frames",JSONArray().apply {
                saved.forEachIndexed { i,s -> val f=s.frame
                    put(JSONObject().put("timestamp_ms",f.timestampMs).put("included",range?.contains(i)==true)
                        .put("pose_present",f.hasPose).put("left_present",f.hasLeftHand).put("right_present",f.hasRightHand)
                        .put("pose",points(f.poseLandmarks,33)).put("left",points(f.leftHandLandmarks,21))
                        .put("right",points(f.rightHandLandmarks,21))
                        .put("canonical",Core5Runtime.array(StandardFullSign225FeatureBuilder.build(f).vector))
                        .put("mediapipe_latency_ms",s.metrics?.totalMs?:JSONObject.NULL)
                        .put("source_timestamp_ns",s.metrics?.sourceTimestampNanos?:JSONObject.NULL)
                        .put("rotation_degrees",s.rotation).put("upright_width",f.sourceWidth).put("upright_height",f.sourceHeight)
                        .put("handedness",JSONArray().apply { f.handObservations.forEach { h ->
                            put(JSONObject().put("reported",h.mediaPipeHandedness).put("slot",h.slot)
                                .put("score",h.handednessScore?:JSONObject.NULL).put("policy",h.physicalSideEstimate))
                        }}))
                }
            })
        output.mkdirs()
        check((output.listFiles()?.sumOf { it.length() }?:0L)<150L*1024*1024) { "Capture quota reached" }
        val file=File(output,id+".json"); check(!file.exists()); file.writeText(json.toString())
        done("SAVED "+id+" / "+mode+" / raw="+json.optString("raw_top1")+" / "+rejection)
    }
}
