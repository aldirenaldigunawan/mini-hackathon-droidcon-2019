package com.google.firebase.samples.apps.mlkit.kotlin.facedetection

import android.content.Context
import android.graphics.*
import android.graphics.Color.rgb
import android.graphics.Paint.Style
import android.graphics.drawable.Drawable
import com.google.firebase.ml.vision.common.FirebaseVisionPoint
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark.*
import com.google.firebase.samples.apps.mlkit.R
import com.google.firebase.samples.apps.mlkit.common.GraphicOverlay

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
class FaceGraphic(
        private val mContext: Context,
        overlay: GraphicOverlay,
        private val firebaseVisionFace: FirebaseVisionFace?,
        private val facing: Int,
        private val overlayBitmap: Bitmap?
) :
        GraphicOverlay.Graphic(overlay) {

    private val mHappyStarGraphic: Drawable? = mContext.getDrawable(R.drawable.happy_star)
    private val mSadGraphic: Drawable? = mContext.getDrawable(R.drawable.sad)

    private val mEyeWhitesPaint: Paint = Paint()
    private val mEyeIrisPaint: Paint
    private val mEyeOutlinePaint: Paint
    private val mEyeLidPaint: Paint

    // Face coordinate and dimension data
    private var mLeftEyeOpen: Boolean = false
    private var mRightEyeOpen: Boolean = false
    private var mIsSmiling: Emotion = Emotion.Normal

    // We want each iris to move independently,
    // so each one gets its own physics engine.
    private val mLeftPhysics = EyePhysics()
    private val mRightPhysics = EyePhysics()

    private val powderColorBlue = rgb(176, 224, 230)
    private val saddleBrownColor = rgb(139, 69, 19)

    init {
        mEyeWhitesPaint.color = Color.WHITE
        mEyeWhitesPaint.style = Style.FILL

        mEyeLidPaint = Paint()
        mEyeLidPaint.color = powderColorBlue
        mEyeLidPaint.style = Style.FILL

        mEyeIrisPaint = Paint()
        mEyeIrisPaint.color = saddleBrownColor
        mEyeIrisPaint.style = Style.FILL

        mEyeOutlinePaint = Paint()
        mEyeOutlinePaint.color = Color.BLACK
        mEyeOutlinePaint.style = Style.STROKE
        mEyeOutlinePaint.strokeWidth = 5f

    }

    override fun draw(canvas: Canvas) {
        val face = firebaseVisionFace ?: return

        val detectLeftPosition = getPosition(face, LEFT_EYE)
        val detectRightPosition = getPosition(face, RIGHT_EYE)
        val detectNoseBasePosition = getPosition(face, NOSE_BASE)
        val detectMouthLeftPosition = getPosition(face, MOUTH_LEFT)
        val detectBottomMouthPosition = getPosition(face, MOUTH_BOTTOM)
        val detectMouthRightPosition = getPosition(face, MOUTH_RIGHT)
        if (detectLeftPosition == null ||
                detectRightPosition == null ||
                detectNoseBasePosition == null ||
                detectMouthLeftPosition == null ||
                detectBottomMouthPosition == null ||
                detectMouthRightPosition == null) return

        mIsSmiling = when {
            face.smilingProbability < 0.4 -> Emotion.Sad
            face.smilingProbability in 0.4..0.7 -> Emotion.Normal
            else -> Emotion.Happy
        }
        mLeftEyeOpen = face.leftEyeOpenProbability > 0.4
        mRightEyeOpen = face.rightEyeOpenProbability > 0.4
        // to view coordinates and dimensions.
        val leftEyePosition = PointF(translateX(detectLeftPosition.x),
                translateY(detectLeftPosition.y))
        val rightEyePosition = PointF(translateX(detectRightPosition.x),
                translateY(detectRightPosition.y))

        // Calculate the distance between the eyes using Pythagoras' formula,
        // and we'll use that distance to set the size of the eyes and irises.
        val distance = Math.sqrt(
                ((rightEyePosition.x - leftEyePosition.x) * (rightEyePosition.x - leftEyePosition.x) + (rightEyePosition.y - leftEyePosition.y) * (rightEyePosition.y - leftEyePosition.y)).toDouble()).toFloat()
        val eyeRadius = EYE_RADIUS_PROPORTION * distance
        val irisRadius = IRIS_RADIUS_PROPORTION * distance

        // Draw the eyes.
        val leftIrisPosition = mLeftPhysics.nextIrisPosition(leftEyePosition, eyeRadius, irisRadius)
        drawEye(canvas, leftEyePosition, eyeRadius, leftIrisPosition, irisRadius, mLeftEyeOpen, mIsSmiling)
        val rightIrisPosition = mRightPhysics.nextIrisPosition(rightEyePosition, eyeRadius, irisRadius)
        drawEye(canvas, rightEyePosition, eyeRadius, rightIrisPosition, irisRadius, mRightEyeOpen, mIsSmiling)


    }

    private fun drawEye(canvas: Canvas, eyePosition: PointF, eyeRadius: Float,
                        irisPosition: PointF, irisRadius: Float, isOpen: Boolean,
                        emotion: Emotion) {
        if (isOpen) {
            canvas.drawCircle(eyePosition.x, eyePosition.y, eyeRadius, mEyeWhitesPaint)
            if (emotion == Emotion.Sad) {
                canvas.drawCircle(irisPosition.x, irisPosition.y, irisRadius, mEyeIrisPaint)
                mSadGraphic?.setBounds((irisPosition.x - irisRadius).toInt(),
                        (irisPosition.y - irisRadius).toInt(),
                        (irisPosition.x + irisRadius).toInt(),
                        (irisPosition.y + irisRadius).toInt())
                mSadGraphic?.draw(canvas)
            } else{
                mHappyStarGraphic?.setBounds((irisPosition.x - irisRadius).toInt(),
                        (irisPosition.y - irisRadius).toInt(),
                        (irisPosition.x + irisRadius).toInt(),
                        (irisPosition.y + irisRadius).toInt())
                mHappyStarGraphic?.draw(canvas)
            }
        } else {
            canvas.drawCircle(eyePosition.x, eyePosition.y, eyeRadius, mEyeLidPaint)
            val y = eyePosition.y
            val start = eyePosition.x - eyeRadius
            val end = eyePosition.x + eyeRadius
            canvas.drawLine(start, y, end, y, mEyeOutlinePaint)
        }
        canvas.drawCircle(eyePosition.x, eyePosition.y, eyeRadius, mEyeOutlinePaint)
    }

    private fun getPosition(face: FirebaseVisionFace, landmarkID: Int): FirebaseVisionPoint? {
        val landmark = face.getLandmark(landmarkID)
        return landmark?.position
    }

    companion object {
        private const val BOX_STROKE_WIDTH = 5.0f

        private const val EYE_RADIUS_PROPORTION = 0.45f
        private const val IRIS_RADIUS_PROPORTION = EYE_RADIUS_PROPORTION / 2.0f
        private const val ID_TEXT_SIZE = 60.0f
    }

    enum class Emotion {
        Happy, Sad, Normal
    }
}
