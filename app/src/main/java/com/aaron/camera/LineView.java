package com.aaron.camera;

/**
 * Created by 创宇 on 2017/3/1.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.Queue;

public class LineView extends View {

    private Queue<Integer> mQueuePoint = new LinkedList<>();

    Paint mPaint = new Paint();

    public LineView(Context context) {
        super(context);
    }

    public LineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LineView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        mPaint.setColor(Color.RED);
        mPaint.setAntiAlias(true);

        int tempY = 0xffffff;
        int curX = 0;
        for (int curY : mQueuePoint) {
            if (tempY != 0xffffff) {
                canvas.drawLine(curX - 30, tempY, curX, curY, mPaint);
                canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            }
            tempY = curY;
            curX += 30;
        }
    }
    int size = 0;

    public void setLinePoint(int curY)
    {
        if (size < 20)
            size = mQueuePoint.size();
        else
            mQueuePoint.poll();
        mQueuePoint.offer(curY);
        invalidate();
    }
}