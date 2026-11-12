package com.rockstargames.gtasa.launcher.provider;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Storage Access Framework DocumentsProvider exposing ROMP by HARFA game & mod files
 * to the Android Files app even on Android 11+ scoped storage devices.
 */
public class GameFilesDocumentsProvider extends DocumentsProvider {
    private static final String ROOT_ID = "romp_files_root";
    private static final String[] DEFAULT_ROOT_PROJECTION = {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    };
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
    };

    private File getBaseDir() throws FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null) throw new FileNotFoundException("Provider context unavailable");
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) throw new FileNotFoundException("Game storage unavailable");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File resolveDocId(String documentId) throws FileNotFoundException {
        File base = getBaseDir();
        if (documentId == null || ROOT_ID.equals(documentId) || documentId.isEmpty()) {
            return base;
        }
        String rel = documentId.startsWith(ROOT_ID + "/")
                ? documentId.substring(ROOT_ID.length() + 1)
                : documentId;
        File candidate = new File(base, rel);
        try {
            String baseCanon = base.getCanonicalPath();
            String candCanon = candidate.getCanonicalPath();
            if (!candCanon.equals(baseCanon) && !candCanon.startsWith(baseCanon + File.separator)) {
                throw new FileNotFoundException("Path outside game root");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Invalid document path: " + e.getMessage());
        }
        return candidate;
    }

    private String getDocIdForFile(File file) throws FileNotFoundException {
        File base = getBaseDir();
        try {
            String baseCanon = base.getCanonicalPath();
            String fileCanon = file.getCanonicalPath();
            if (fileCanon.equals(baseCanon)) return ROOT_ID;
            if (fileCanon.startsWith(baseCanon + File.separator)) {
                String rel = fileCanon.substring(baseCanon.length() + 1).replace(File.separatorChar, '/');
                return ROOT_ID + "/" + rel;
            }
        } catch (IOException ignored) {}
        throw new FileNotFoundException("Cannot resolve document ID");
    }

    private static String getMimeType(File file) {
        if (file.isDirectory()) return Document.MIME_TYPE_DIR;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.US);
            if ("lua".equals(ext) || "ini".equals(ext) || "cfg".equals(ext) || "log".equals(ext) || "txt".equals(ext)) {
                return "text/plain";
            }
            if ("json".equals(ext)) return "application/json";
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private void includeFile(MatrixCursor result, String docId, File file) throws FileNotFoundException {
        if (docId == null) docId = getDocIdForFile(file);
        int flags = 0;
        if (file.isDirectory()) {
            if (file.canWrite()) {
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
            }
        } else if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }
        File base = getBaseDir();
        if (!file.equals(base) && file.getParentFile() != null && file.getParentFile().canWrite()) {
            flags |= Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME;
        }

        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, file.equals(base) ? "ROMP by HARFA Files" : file.getName());
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_MIME_TYPE, getMimeType(file));
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_FLAGS, flags);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        File base = getBaseDir();
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(Root.COLUMN_ICON, getContext().getApplicationInfo().icon);
        row.add(Root.COLUMN_TITLE, "ROMP by HARFA");
        row.add(Root.COLUMN_SUMMARY, "Berkas Gim & Mod");
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_ID);
        row.add(Root.COLUMN_AVAILABLE_BYTES, base.getFreeSpace());
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        includeFile(result, documentId, resolveDocId(documentId));
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        File parent = resolveDocId(parentDocumentId);
        File[] children = parent.listFiles();
        if (children != null) {
            for (File child : children) {
                includeFile(result, null, child);
            }
        }
        return result;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        if (ROOT_ID.equals(parentDocumentId)) return true;
        return documentId != null && documentId.startsWith(parentDocumentId + "/");
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        File file = resolveDocId(documentId);
        int accessMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, accessMode);
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        File parent = resolveDocId(parentDocumentId);
        if (displayName == null || displayName.contains("/") || displayName.contains("..")) {
            throw new FileNotFoundException("Invalid display name");
        }
        File target = new File(parent, displayName);
        if (Document.MIME_TYPE_DIR.equals(mimeType)) {
            if (!target.mkdir()) {
                throw new FileNotFoundException("Could not create directory: " + target.getName());
            }
        } else {
            try {
                if (!target.createNewFile()) {
                    throw new FileNotFoundException("Could not create file: " + target.getName());
                }
            } catch (IOException e) {
                throw new FileNotFoundException("Create failed: " + e.getMessage());
            }
        }
        return getDocIdForFile(target);
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        if (ROOT_ID.equals(documentId)) {
            throw new FileNotFoundException("Root cannot be deleted");
        }
        File target = resolveDocId(documentId);
        if (!deleteRecursive(target)) {
            throw new FileNotFoundException("Could not delete " + target.getName());
        }
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        if (ROOT_ID.equals(documentId)) {
            throw new FileNotFoundException("Root cannot be renamed");
        }
        if (displayName == null || displayName.contains("/") || displayName.contains("..")) {
            throw new FileNotFoundException("Invalid target name");
        }
        File src = resolveDocId(documentId);
        File dst = new File(src.getParentFile(), displayName);
        if (!src.renameTo(dst)) {
            throw new FileNotFoundException("Could not rename " + src.getName());
        }
        return getDocIdForFile(dst);
    }

    private static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) return false;
                }
            }
        }
        return file.delete();
    }
}

