package com.rockstargames.gtasa.launcher.data;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages mobile-compatible GTA SA Mod Loader & Game Textures (texdb):
 * - Applies DFF/TXD/PNG/timecyc/sound mods inside mods/modpack/
 * - Supports master mod_loader toggle without losing files
 * - Inspects DFF/TXD RenderWare headers for quick 3D asset verification
 * - Detects and reports unsupported PC binaries (.asi/.cs/.cleo) cleanly
 * - Installs & removes texdb ZIP custom textures
 */
public class ModLoaderManager {

    public static class ModEntry {
        public final String relativePath;
        public final String category;
        public final long sizeBytes;
        public final boolean enabled;
        public final File file;

        public ModEntry(String relativePath, String category, File file, boolean enabled) {
            this.relativePath = relativePath;
            this.category = category;
            this.file = file;
            this.sizeBytes = file.length();
            this.enabled = enabled;
        }
    }

    public static class ImportReport {
        public int appliedCount = 0;
        public final List<String> appliedFiles = new ArrayList<>();
        public final List<String> skippedUnsupported = new ArrayList<>();
    }

    public static File getBaseDir(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir != null ? dir : context.getFilesDir();
    }

    public static File getModpackDir(Context context) {
        return new File(getBaseDir(context), "mods/modpack");
    }

    public static File getModpackDisabledDir(Context context) {
        return new File(getBaseDir(context), "mods/modpack_disabled");
    }

    public static void applyMasterToggle(Context context, boolean enable) {
        File active = getModpackDir(context);
        File disabled = getModpackDisabledDir(context);
        if (enable) {
            if (!active.exists() && disabled.exists()) {
                disabled.renameTo(active);
            }
            if (!active.exists()) active.mkdirs();
        } else {
            if (active.exists() && !disabled.exists()) {
                active.renameTo(disabled);
            }
        }
    }

    public static List<ModEntry> listMods(Context context) {
        List<ModEntry> result = new ArrayList<>();
        File activeRoot = getModpackDir(context);
        File root = activeRoot.exists() ? activeRoot : getModpackDisabledDir(context);
        boolean rootEnabled = activeRoot.exists();
        if (!root.exists()) return result;

        collectMods(root, root, rootEnabled, result);
        return result;
    }

    private static void collectMods(File root, File dir, boolean rootEnabled, List<ModEntry> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : children) {
            if (f.isDirectory()) {
                collectMods(root, f, rootEnabled, out);
            } else {
                String rel = root.toURI().relativize(f.toURI()).getPath();
                String lower = rel.toLowerCase(Locale.US);
                boolean enabled = rootEnabled && !lower.endsWith(".disabled");
                String cat = categorizeRel(lower);
                out.add(new ModEntry(rel, cat, f, enabled));
            }
        }
    }

    private static String categorizeRel(String lower) {
        if (lower.startsWith("textures/") || lower.endsWith(".png")) return "Texture";
        if (lower.startsWith("shared/") || lower.endsWith(".txd") || lower.endsWith(".txd.disabled")) return "TXD Dict";
        if (lower.endsWith(".dff") || lower.endsWith(".dff.disabled")) return "3D Model";
        if (lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".wav.disabled") || lower.endsWith(".mp3.disabled")) return "Audio";
        return "Data";
    }

    public static boolean toggleModEntry(ModEntry entry) {
        if (entry == null || !entry.file.exists()) return false;
        String name = entry.file.getName();
        File target = name.toLowerCase(Locale.US).endsWith(".disabled")
                ? new File(entry.file.getParentFile(), name.substring(0, name.length() - ".disabled".length()))
                : new File(entry.file.getParentFile(), name + ".disabled");
        return entry.file.renameTo(target);
    }

    public static ImportReport importModFile(Context context, InputStream in, String originalName, boolean modEnabled) {
        ImportReport report = new ImportReport();
        if (in == null || originalName == null) return report;
        String lower = originalName.toLowerCase(Locale.US);
        File targetBase = modEnabled ? getModpackDir(context) : getModpackDisabledDir(context);
        if (!targetBase.exists()) targetBase.mkdirs();

        try {
            if (lower.endsWith(".zip")) {
                return importModpackZip(context, in, modEnabled);
            }
            if (isUnsupportedPcMod(lower)) {
                report.skippedUnsupported.add(originalName + " (script PC/CLEO tidak didukung engine native)");
                return report;
            }
            File dest = routeModDestination(targetBase, originalName);
            if (dest.getParentFile() != null && !dest.getParentFile().exists()) dest.getParentFile().mkdirs();
            copyStreamToFile(in, dest);
            report.appliedCount++;
            report.appliedFiles.add(dest.getName());
        } catch (Exception e) {
            report.skippedUnsupported.add(originalName + " (gagal impor: " + e.getMessage() + ")");
        }
        return report;
    }

    public static ImportReport importModpackZip(Context context, InputStream zipStream, boolean modEnabled) throws Exception {
        ImportReport report = new ImportReport();
        File targetBase = modEnabled ? getModpackDir(context) : getModpackDisabledDir(context);
        if (!targetBase.exists()) targetBase.mkdirs();

        String baseCanon = targetBase.getCanonicalPath();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..")) {
                    report.skippedUnsupported.add(name + " (unsafe path)");
                    zis.closeEntry();
                    continue;
                }
                String lower = name.toLowerCase(Locale.US);
                if (isUnsupportedPcMod(lower)) {
                    report.skippedUnsupported.add(name + " (CLEO/ASI/MoonLoader tidak didukung di Modpack)");
                    zis.closeEntry();
                    continue;
                }
                String baseName = new File(name).getName();
                if (baseName.isEmpty() || !isSupportedModFile(lower)) {
                    report.skippedUnsupported.add(name);
                    zis.closeEntry();
                    continue;
                }
                File outFile = routeModDestination(targetBase, name);
                if (!outFile.getCanonicalPath().startsWith(baseCanon + File.separator)) {
                    zis.closeEntry();
                    continue;
                }
                if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) outFile.getParentFile().mkdirs();
                copyStreamToFileNoClose(zis, outFile);
                report.appliedCount++;
                report.appliedFiles.add(baseName);
                zis.closeEntry();
            }
        }
        return report;
    }

    public static int importTexdbZip(Context context, InputStream zipStream) throws Exception {
        File texdbDir = new File(getBaseDir(context), "texdb/custom");
        if (!texdbDir.exists()) texdbDir.mkdirs();
        String baseCanon = texdbDir.getCanonicalPath();
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && !entry.getName().contains("..")) {
                    String rel = entry.getName().replace('\\', '/');
                    if (rel.startsWith("texdb/")) rel = rel.substring("texdb/".length());
                    File target = new File(texdbDir, rel);
                    if (target.getCanonicalPath().startsWith(baseCanon + File.separator)) {
                        if (target.getParentFile() != null && !target.getParentFile().exists()) target.getParentFile().mkdirs();
                        copyStreamToFileNoClose(zis, target);
                        count++;
                    }
                }
                zis.closeEntry();
            }
        }
        return count;
    }

    public static int clearCustomTexdb(Context context) {
        File custom = new File(getBaseDir(context), "texdb/custom");
        return deleteRecursiveCount(custom);
    }

    public static String inspectRenderWareHeader(File file) {
        if (file == null || !file.exists() || file.length() < 12) return "Ukuran berkas tidak valid.";
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] hdr = new byte[12];
            if (fis.read(hdr) != 12) return "Gagal membaca header RenderWare.";
            int type = (hdr[0] & 0xFF) | ((hdr[1] & 0xFF) << 8) | ((hdr[2] & 0xFF) << 16) | ((hdr[3] & 0xFF) << 24);
            int size = (hdr[4] & 0xFF) | ((hdr[5] & 0xFF) << 8) | ((hdr[6] & 0xFF) << 16) | ((hdr[7] & 0xFF) << 24);
            int ver = (hdr[8] & 0xFF) | ((hdr[9] & 0xFF) << 8) | ((hdr[10] & 0xFF) << 16) | ((hdr[11] & 0xFF) << 24);
            String typeLabel = (type == 0x10) ? "RW Clump (3D Model .dff)"
                    : (type == 0x16) ? "RW TexDictionary (.txd)"
                    : String.format(Locale.US, "RW Chunk 0x%X", type);
            return typeLabel + "\n• Payload Size: " + size + " bytes\n• RW Build ID: 0x" + Integer.toHexString(ver).toUpperCase(Locale.US);
        } catch (Exception e) {
            return "Gagal inspeksi: " + e.getMessage();
        }
    }

    private static File routeModDestination(File modpackRoot, String rawPath) {
        String lower = rawPath.toLowerCase(Locale.US);
        String baseName = new File(rawPath).getName();
        if (lower.endsWith(".txd")) return new File(modpackRoot, "shared/" + baseName);
        if (lower.endsWith(".png")) return new File(modpackRoot, "textures/" + baseName);
        if (lower.endsWith(".wav") || lower.endsWith(".mp3")) return new File(modpackRoot, "sounds/" + baseName);
        if ("timecyc.dat".equalsIgnoreCase(baseName)) return new File(modpackRoot, "timecyc.dat");
        if ("txdassign.txt".equalsIgnoreCase(baseName)) return new File(modpackRoot, "txdassign.txt");
        return new File(modpackRoot, baseName);
    }

    private static boolean isSupportedModFile(String lower) {
        return lower.endsWith(".dff") || lower.endsWith(".txd") || lower.endsWith(".png")
                || lower.endsWith(".wav") || lower.endsWith(".mp3")
                || lower.endsWith("timecyc.dat") || lower.endsWith("txdassign.txt")
                || lower.endsWith(".dat") || lower.endsWith(".col") || lower.endsWith(".ifp");
    }

    private static boolean isUnsupportedPcMod(String lower) {
        return lower.endsWith(".cs") || lower.endsWith(".asi") || lower.endsWith(".cleo")
                || lower.endsWith(".dll") || lower.endsWith(".sf") || lower.endsWith(".exe");
    }

    private static void copyStreamToFile(InputStream in, File dest) throws Exception {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        } finally {
            in.close();
        }
    }

    private static void copyStreamToFileNoClose(InputStream in, File dest) throws Exception {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }

    private static int deleteRecursiveCount(File f) {
        if (f == null || !f.exists()) return 0;
        int cnt = 0;
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) {
                for (File c : list) cnt += deleteRecursiveCount(c);
            }
        }
        if (f.delete()) cnt++;
        return cnt;
    }
}

