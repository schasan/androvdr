//
// based on:  http://stackoverflow.com/questions/5033012/auto-scale-textview-text-to-fit-within-bounds
//

package de.androvdr.widget;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Text view that auto adjusts text size to fit within the view. If the text
 * size equals the minimum text size and still does not fit, append with an
 * ellipsis.
 *
 * 2011-10-29 changes by Alan Jay Weiner
 *              * change to fit both vertically and horizontally  
 *              * test mTextSize for 0 in resizeText() to fix exception in Layout Editor
 *
 * @author Chase Colburn
 * @since Apr 4, 2011
 */
public class TextResizeButton extends TextResizeView {

    // Default text size for all buttons, calculated by InitTextSize
    private static float sTextSize = 0;
    
	public TextResizeButton(Context context) {
		super(context);
	}

	public TextResizeButton(Context context, AttributeSet attrs) {
		super(context, attrs);
        mTextSize = (sTextSize > 0) ? sTextSize : getTextSize();
	}

	public TextResizeButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
        mTextSize = (sTextSize > 0) ? sTextSize : getTextSize();
	}

    /**
     * Reset default text size
     */
    public static void resetDefaultTextSize() {
    	sTextSize = 0;
    }

    /**
     * Set calculated text size as default for all TextResizeButtons
     */
    public void setTextSizeAsDefault(int width, int height) {
    	int widthLimit = width - getPaddingLeft() - getPaddingRight();
    	int heightLimit = height - getPaddingTop() - getPaddingBottom();
    	resizeText(widthLimit, heightLimit);
    	sTextSize = getTextSize();
    }
}
