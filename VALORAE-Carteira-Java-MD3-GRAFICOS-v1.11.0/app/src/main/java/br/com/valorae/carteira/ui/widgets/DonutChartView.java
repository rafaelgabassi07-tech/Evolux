package br.com.valorae.carteira.ui.widgets;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.*;

public class DonutChartView extends View {
    public static class Segment {
        public final String label;
        public final float value;
        public final int color;
        public Segment(String label, float value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Segment> segments = new ArrayList<>();
    private float total = 0f;

    public DonutChartView(Context c) { super(c); init(); }
    public DonutChartView(Context c, AttributeSet a) { super(c, a); init(); }
    public DonutChartView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFF101828);
        textPaint.setTextSize(36f);
        setMinimumHeight(240);
    }

    public void setSegments(List<Segment> data) {
        segments.clear();
        total = 0f;
        if (data != null) {
            for (Segment s : data) {
                if (s.value > 0f) {
                    segments.add(s);
                    total += s.value;
                }
            }
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h) * 0.72f;
        float stroke = Math.max(24f, size * 0.14f);
        paint.setStrokeWidth(stroke);
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        arcRect.set(left, top, left + size, top + size);

        paint.setColor(0xFFE5E7EB);
        canvas.drawArc(arcRect, 0, 360, false, paint);

        float start = -90f;
        if (total > 0f) {
            for (Segment segment : segments) {
                paint.setColor(segment.color);
                float sweep = 360f * (segment.value / total);
                canvas.drawArc(arcRect, start, sweep, false, paint);
                start += sweep;
            }
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(Math.round(total) + "%", w / 2f, h / 2f + 12f, textPaint);
            textPaint.setTypeface(Typeface.DEFAULT);
            textPaint.setTextSize(26f);
            canvas.drawText("Alocação", w / 2f, h / 2f + 48f, textPaint);
            textPaint.setTextSize(36f);
        } else {
            canvas.drawText("Sem dados", w / 2f, h / 2f + 12f, textPaint);
        }
    }
}
