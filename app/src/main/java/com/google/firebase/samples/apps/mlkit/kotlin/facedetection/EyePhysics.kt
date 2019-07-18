package com.google.firebase.samples.apps.mlkit.kotlin.facedetection

import android.graphics.PointF
import android.os.SystemClock

/**
 * Simulates the physics of motion for an iris which moves within a googly eye.  The iris moves
 * independently of the motion of the face/eye, and moves according to the following forces:
 *
 *
 *
 *
 *  1. Gravity - downward acceleration.
 *
 *  1. Friction - deceleration; opposing motion
 *
 *  1. Bounce - acceleration in the opposite direction of motion when the iris hits the side of the
 * eye (e.g., due to a jerking motion which suddenly moves the face in frame).  Note that this is
 * the only way to get the iris to move horizontally, since gravity only accelerates downward.
 *
 *
 * The simulation is configured to run at a universal real time rate, regardless of the performance
 * of the device in which it is run and how frequently updates are received.
 */
internal class EyePhysics {


    private var mLastUpdateTimeMs = SystemClock.elapsedRealtime()

    private var mEyePosition: PointF? = null
    private var mEyeRadius: Float = 0.toFloat()

    private var mIrisPosition: PointF? = null
    private var mIrisRadius: Float = 0.toFloat()

    // Velocity is independent of the final rendering coordinate system, so that we don't have to
    // change it as the eye gets bigger or smaller by forward and backward motion.  This will be
    // scaled up proportional to the eye size when updating position.
    private var vx = 0.0f
    private var vy = 0.0f

    // Keep track of bounces that immediately occur consecutively, since this means that the
    // iris is bouncing too fast.  When this happens, we dampen the velocity to avoid infinite
    // bounces.
    private var mConsecutiveBounces = 0

    /**
     * The iris is stopped if it is at the bottom of the eye and its velocity is zero.
     */
    private val isStopped: Boolean
        get() {
            if (mEyePosition!!.y >= mIrisPosition!!.y) {
                return false
            }

            val irisOffsetY = mIrisPosition!!.y - mEyePosition!!.y
            val maxDistance = mEyeRadius - mIrisRadius
            return if (irisOffsetY < maxDistance) {
                false
            } else isZero(vx) && isZero(vy)

        }

    //==============================================================================================
    // Methods
    //==============================================================================================

    /**
     * Generate the next position of the iris based on simulated velocity, eye boundaries, gravity,
     * friction, and bounce momentum.
     */
    fun nextIrisPosition(eyePosition: PointF, eyeRadius: Float, irisRadius: Float): PointF {
        // Correct the current eye position and size based on recent motion of the face within the
        // frame.  Keep the current iris position, if available.
        mEyePosition = eyePosition
        mEyeRadius = eyeRadius
        if (mIrisPosition == null) {
            mIrisPosition = eyePosition
        }
        mIrisRadius = irisRadius

        // Keep track of time, so that we can consistently update the simulation proportionally to
        // how much time has elapsed.  This makes the animation rate device-independent.  All of the
        // velocity changes below are pro-rated based on this.
        val nowMs = SystemClock.elapsedRealtime()
        val elapsedTimeMs = nowMs - mLastUpdateTimeMs
        val simulationRate = elapsedTimeMs.toFloat() / TIME_PERIOD_MS
        mLastUpdateTimeMs = nowMs

        if (!isStopped) {
            // Only apply gravity when the iris is not stopped at the bottom of the eye.
            vy += GRAVITY * simulationRate
        }

        // Apply friction in the opposite direction of motion, so that the iris slows in the absence
        // of other head motion.
        vx = applyFriction(vx, simulationRate)
        vy = applyFriction(vy, simulationRate)

        // Update the iris position based on velocity.  Since velocity is size-independent, scale by
        // the iris radius to get the change in position.
        val x = mIrisPosition!!.x + vx * mIrisRadius * simulationRate
        val y = mIrisPosition!!.y + vy * mIrisRadius * simulationRate
        mIrisPosition = PointF(x, y)

        // Correct the position and velocity of the iris if it has gone out of bounds, guaranteeing
        // that the returned result is at a valid position within the eye.
        makeIrisInBounds(simulationRate)

        return mIrisPosition as PointF
    }

    /**
     * Friction slows velocity in the opposite direction of motion, until zero velocity is reached.
     */
    private fun applyFriction(velocity: Float, simulationRate: Float): Float {
        var velocity = velocity
        if (isZero(velocity)) {
            velocity = 0.0f
        } else if (velocity > 0) {
            velocity = Math.max(0.0f, velocity - FRICTION * simulationRate)
        } else {
            velocity = Math.min(0.0f, velocity + FRICTION * simulationRate)
        }
        return velocity
    }

    /**
     * Correct the iris position to be in-bounds within the eye, if it is now out of bounds.  Being
     * out of bounds could have been due to a sudden movement of the head and/or camera, or the
     * result of just bouncing/rolling around.
     *
     *
     *
     * In addition, modify the velocity to cause a bounce in the opposite direction.
     */
    private fun makeIrisInBounds(simulationRate: Float) {
        val irisOffsetX = mIrisPosition!!.x - mEyePosition!!.x
        val irisOffsetY = mIrisPosition!!.y - mEyePosition!!.y

        val maxDistance = mEyeRadius - mIrisRadius
        val distance = Math.sqrt(Math.pow(irisOffsetX.toDouble(), 2.0) + Math.pow(irisOffsetY.toDouble(), 2.0)).toFloat()
        if (distance <= maxDistance) {
            // The iris is in bounds, so no correction is necessary.
            mConsecutiveBounces = 0
            return
        }

        // Accumulate a consecutive bounce count, in order to dampen the momentum of a quickly
        // moving iris.  Two or more bounces in a row indicates that the iris is moving so fast that
        // it doesn't even travel inside the eye.  We progressively slow the velocity using this
        // count until this is no longer the case.
        mConsecutiveBounces++

        // Move the iris back to where it would have been when it would have contacted the side of
        // the eye.
        val ratio = maxDistance / distance
        val x = mEyePosition!!.x + ratio * irisOffsetX
        val y = mEyePosition!!.y + ratio * irisOffsetY

        // Update the velocity direction and magnitude to cause a bounce.

        val dx = x - mIrisPosition!!.x
        vx = applyBounce(vx, dx, simulationRate) / mConsecutiveBounces

        val dy = y - mIrisPosition!!.y
        vy = applyBounce(vy, dy, simulationRate) / mConsecutiveBounces

        mIrisPosition = PointF(x, y)
    }

    /**
     * Update velocity in response to bouncing off the sides of the eye (i.e., when iris hits the
     * bottom or the eye moves quickly).  This is the only way to gain horizontal velocity, since
     * there is no other horizontal force.
     */
    private fun applyBounce(velocity: Float, distOutOfBounds: Float, simulationRate: Float): Float {
        var velocity = velocity
        if (isZero(distOutOfBounds)) {
            // No bounce needed, since we are still in bounds along this dimension.
            return velocity
        }

        // Reverse velocity to create a bounce in the opposite direction.
        velocity *= -1f

        // If distOutOfBounds was large, this indicates that the iris was whacked against the side
        // of the eye quickly.  Add an additional velocity factor to account for the force gained by
        // this quick movement, based upon how much it was out of bounds.
        val bounce = BOUNCE_MULTIPLIER * Math.abs(distOutOfBounds / mIrisRadius)
        if (velocity > 0) {
            velocity += bounce * simulationRate
        } else {
            velocity -= bounce * simulationRate
        }

        return velocity
    }

    /**
     * Allow for a small tolerance in floating point values in considering whether a value is zero.
     */
    private fun isZero(num: Float): Boolean {
        return num < ZERO_TOLERANCE && num > -1 * ZERO_TOLERANCE
    }

    companion object {

        private val TAG = "EyePhysics"
        // The friction and gravity values below are set relative to a specific time period.  This
        // allows the simulation to run at the same rate, regardless of whether it is running on a slow
        // or fast device or if there are temporary performance variations on the device.
        private val TIME_PERIOD_MS: Long = 1000

        private val FRICTION = 2.2f
        private val GRAVITY = 40.0f

        private val BOUNCE_MULTIPLIER = 20.0f

        // Allow slightly non-zero values to be considered to be zero, to converge to zero more quickly.
        private val ZERO_TOLERANCE = 0.001f
    }
}
