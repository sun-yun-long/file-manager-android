package com.example.phonefilemanager;

import java.io.File;

public class FileItem {
    public final File file;
    public final FileCategory category;

    public FileItem(File file, FileCategory category) {
        this.file = file;
        this.category = category;
    }
}
