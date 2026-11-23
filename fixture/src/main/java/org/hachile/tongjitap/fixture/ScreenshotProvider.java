package org.hachile.tongjitap.fixture;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Test-only image source in the separate fixture APK. Never included in the published APK. */
public final class ScreenshotProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        String kind = uri.getLastPathSegment();
        if (!"full".equals(kind) && !"cropped".equals(kind) && !"broken".equals(kind) && !"transparent".equals(kind))
            throw new FileNotFoundException("Unknown fixture");
        int width = Integer.parseInt(uri.getQueryParameter("width")), height = Integer.parseInt(uri.getQueryParameter("height"));
        if (width < 100 || height < 100 || (long) width * height > 16_000_000) throw new FileNotFoundException("Invalid fixture size");
        File image = new File(getContext().getCacheDir(), "selection-" + kind + ".png");
        try (FileOutputStream out = new FileOutputStream(image)) {
            if ("broken".equals(kind)) out.write(new byte[]{1, 2, 3, 4});
            else {
                Bitmap bitmap = Bitmap.createBitmap(width, "cropped".equals(kind) ? height / 2 : height, Bitmap.Config.ARGB_8888);
                if (!"transparent".equals(kind)) {
                    bitmap.eraseColor(Color.WHITE);
                    Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(); paint.setColor(0xFF12AB34);
                    canvas.drawRect(0, 0, width / 2f, height, paint);
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle();
            }
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
        return ParcelFileDescriptor.open(image, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
