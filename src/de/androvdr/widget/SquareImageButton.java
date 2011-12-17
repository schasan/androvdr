package de.androvdr.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

public class SquareImageButton extends ImageButton {

	public SquareImageButton(Context context) {
		super(context);
	}

	public SquareImageButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SquareImageButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	/**
	 * @see android.view.View#measure(int, int)
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		int width = MeasureSpec.getSize(widthMeasureSpec);
	    int height = MeasureSpec.getSize(heightMeasureSpec);

	    if (width == 0)
	    	width = getSuggestedMinimumWidth();
	    if (height == 0)
	    	height = getSuggestedMinimumHeight();
	    
	    if (width != height) {
	        width = height;
	    }

	    super.onMeasure(
	            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
	            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
	    );
	}
}
