package com.voxgest.dryrun
import kotlin.math.sqrt

object Core5Contract {
    const val PROFILE="FSL_CORE5_REBASE_V1"
    const val VERSION="core5_tasks_fullsign225_timestamp48_v1"
    val labels=listOf("HELLO","THANK YOU","YES","NO","UNDERSTAND")
    fun sanitize(f:LandmarkFrame):LandmarkFrame = f.copy(
        leftHandLandmarks=f.leftHandLandmarks.takeIf { f.handObservations.count { h -> h.mediaPipeHandedness.equals("left",true) }==1 },
        rightHandLandmarks=f.rightHandLandmarks.takeIf { f.handObservations.count { h -> h.mediaPipeHandedness.equals("right",true) }==1 })
    fun envelope(frames:List<LandmarkFrame>):IntRange? {
        val active=frames.indices.filter { frames[it].hasPose && frames[it].hasAnyHand }
        if(active.size<2) return null
        val lo=frames.indexOfFirst { it.timestampMs>=frames[active.first()].timestampMs-100 }
        val hi=frames.indexOfLast { it.timestampMs<=frames[active.last()].timestampMs+100 }
        return lo..hi
    }
    fun resample(x:List<FloatArray>,times:List<Long>,length:Int=48):Array<FloatArray> {
        require(x.size>=2 && x.size==times.size && length>=2)
        require(x.all { it.size==225 && it.all(Float::isFinite) })
        require(times.zipWithNext().all { it.second>it.first })
        return Array(length) { i ->
            val target=times.first().toDouble()+i.toDouble()*(times.last()-times.first())/(length-1)
            var upper=times.indexOfFirst { it.toDouble()>=target }
            if(upper<0) upper=times.lastIndex
            val lower=(upper-1).coerceAtLeast(0); val span=times[upper]-times[lower]
            val alpha=if(span==0L) 0f else ((target-times[lower])/span).toFloat()
            FloatArray(225) { j -> x[lower][j]+(x[upper][j]-x[lower][j])*alpha }
        }
    }
    fun motion(x:List<FloatArray>):Float = if(x.size<2) 0f else x.zipWithNext { a,b ->
        sqrt(a.indices.sumOf { j -> val d=b[j]-a[j]; (d*d).toDouble() }).toFloat()
    }.average().toFloat()
    fun gate(p:FloatArray,frames:List<LandmarkFrame>,x:List<FloatArray>,reason:String):String {
        if(reason in setOf("EVENT_TIMEOUT","POSE_TRACKING_LOST","INCOMPLETE_EVENT_REJECTED",
            "LIFECYCLE_STOP","NO_FRAMES_TIMEOUT","CAPTURE_ERROR","OPERATOR_CANCEL")) return reason
        if(frames.size<8) return "INCOMPLETE_EVENT"
        if(frames.last().timestampMs-frames.first().timestampMs>8000) return "EVENT_TIMEOUT"
        if(frames.count { it.hasPose }.toFloat()/frames.size<.65f) return "LOW_POSE_PRESENCE"
        if(frames.count { it.hasAnyHand }.toFloat()/frames.size<.65f) return "LOW_HAND_PRESENCE"
        if(motion(x)<.02f) return "LOW_TRAJECTORY_MOTION"
        require(p.size==5 && p.all(Float::isFinite))
        val ranked=p.sortedDescending()
        if(ranked[0]<.95f) return "LOW_CONFIDENCE"
        if(ranked[0]-ranked[1]<.05f) return "LOW_MARGIN"
        return "ACCEPTED"
    }
}
