package com.example.phonefilemanager;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class LocalFileProvider extends ContentProvider {
    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".files")
                .appendPath(file.getAbsolutePath())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = checkedFileFrom(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.getLastPathSegment();
        return path == null ? "*/*" : FileUtils.mimeTypeFor(path);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = checkedFileFrom(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
        });
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File fileFrom(Uri uri) {
        String path = uri.getLastPathSegment();
        return new File(path == null ? "" : path);
    }

    private File checkedFileFrom(Uri uri) throws FileNotFoundException {
        File file = fileFrom(uri);
        try {
            File canonicalFile = file.getCanonicalFile();
            File storageRoot = Environment.getExternalStorageDirectory().getCanonicalFile();
            String filePath = canonicalFile.getPath();
            String rootPath = storageRoot.getPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
                throw new FileNotFoundException("Unsupported path");
            }
            if (!canonicalFile.isFile() || !canonicalFile.canRead()) {
                throw new FileNotFoundException("Unreadable file");
            }
            return canonicalFile;
        } catch (IOException e) {
            throw new FileNotFoundException("Invalid path");
        }
    }
}
