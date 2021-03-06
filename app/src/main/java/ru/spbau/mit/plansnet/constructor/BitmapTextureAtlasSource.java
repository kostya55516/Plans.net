package ru.spbau.mit.plansnet.constructor;

import org.andengine.opengl.texture.atlas.bitmap.source.IBitmapTextureAtlasSource;
import org.andengine.opengl.texture.atlas.source.BaseTextureAtlasSource;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Rect;
import android.support.annotation.NonNull;

/**
 * Auxiliary class that helps converting a bitmap into a texture.
 */
public class BitmapTextureAtlasSource extends BaseTextureAtlasSource
        implements IBitmapTextureAtlasSource {

    private final int[] mColors;

    /**
     * Constructor taking a bitmap and an area.
     * @param pBitmap bitmap to be converted.
     * @param pArea the part of the bitmap to be converted.
     */
    public BitmapTextureAtlasSource(@NonNull Bitmap pBitmap, @NonNull Rect pArea) {
        super(0,0, pArea.width(), pArea.height());
        mColors = new int[mTextureWidth * mTextureHeight];
        for (int y = 0; y < pArea.height(); y++) {
            for  (int x = 0; x < pArea.width(); x++) {
                mColors[x + y * mTextureWidth] = pBitmap.getPixel(x + pArea.left,
                        y + pArea.top);
            }
        }
    }

    /**
     * Constructor taking a bitmap.
     * @param pBitmap bitmap to be converted.
     */
    public BitmapTextureAtlasSource(@NonNull Bitmap pBitmap) {
        this(pBitmap, new Rect(0, 0, pBitmap.getWidth(), pBitmap.getHeight()));
    }

    @Override
    public @NonNull Bitmap onLoadBitmap(@NonNull Config pBitmapConfig) {
        return Bitmap.createBitmap(mColors, mTextureWidth, mTextureHeight, Bitmap.Config.ARGB_8888);
    }

    @Override
    public @NonNull IBitmapTextureAtlasSource deepCopy() {
        return new BitmapTextureAtlasSource(Bitmap.createBitmap(mColors, mTextureWidth,
                mTextureHeight, Bitmap.Config.ARGB_8888));
    }

}
