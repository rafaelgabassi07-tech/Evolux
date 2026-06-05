package br.com.valorae.carteira.ui.widgets;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.*;

public class LineChartView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Float> points = new ArrayList<>();

    public LineChartView(Context c) { super(c); init(); }
    public LineChartView(Context c, AttributeSet a) { super(c, a); init(); }
    public LineChartView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        gridPaint.setColor(0xFFE5E7EB);
        gridPaint.setStrokeWidth(2f);
        linePaint.setColor(0xFF1565C0);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(6f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setColor(0x331565C0);
        fillPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(0xFF1565C0);
        setMinimumHeight(220);
    }

    public void setPoints(List<Float> values) {
        points.clear();
        if (values != null) points.addAll(values);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int p = 28;
        for (int i = 0; i < 4; i++) {
            float y = p + ((h - p * 2f) / 3f) * i;
            canvas.drawLine(p, y, w - p, y, gridPaint);
        }
        if (points.size() < 2) return;
        float min = Collections.min(points);
        float max = Collections.max(points);
        if (max == min) max += 1f;
        Path line = new Path();
        Path fill = new Path();
        for (int i = 0; i < points.size(); i++) {
            float x = p + (i * (w - p * 2f) / (float) (points.size() - 1));
            float yNorm = (points.get(i) - min) / (max - min);
            float y = h - p - (yNorm * (h - p * 2f));
            if (i == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, h - p);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
            canvas.drawCircle(x, y, 6f, pointPaint);
        }
        float lastX = p + ((points.size() - 1) * (w - p * 2f) / (float) (points.size() - 1));
        fill.lineTo(lastX, h - p);
        fill.close();
        canvas.drawPath(fill, fillPaint);
        canvas.drawPath(line, linePaint);
    }
}
