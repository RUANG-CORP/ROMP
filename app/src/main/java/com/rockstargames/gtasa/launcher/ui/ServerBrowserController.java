package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.rockstargames.gtasa.launcher.data.ServerItem;
import com.rockstargames.gtasa.launcher.data.ServerRepository;

/**
 * Helper dialogs for editing Favorite servers, entering protected server passwords,
 * and copying/sharing ROMP/ReSA deep join links.
 */
public class ServerBrowserController {

    public interface PasswordResult {
        void onConfirmed(ServerItem updatedServer);
    }

    public static void showEditFavoriteDialog(Context context, ServerRepository repo, ServerItem item, Runnable onSaved) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 8);
        layout.setBackgroundColor(Color.parseColor("#0F172A"));

        EditText etName = new EditText(context);
        etName.setHint("Nama Server");
        etName.setText(item.getName());
        etName.setTextColor(Color.WHITE);
        layout.addView(etName);

        EditText etIp = new EditText(context);
        etIp.setHint("IP / Hostname");
        etIp.setText(item.getIp());
        etIp.setTextColor(Color.WHITE);
        layout.addView(etIp);

        EditText etPort = new EditText(context);
        etPort.setHint("Port (default 7777)");
        etPort.setText(String.valueOf(item.getPort()));
        etPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        etPort.setTextColor(Color.WHITE);
        layout.addView(etPort);

        EditText etPass = new EditText(context);
        etPass.setHint("Kata Sandi Server (opsional)");
        etPass.setText(item.getPassword());
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setTextColor(Color.WHITE);
        layout.addView(etPass);

        new AlertDialog.Builder(context)
                .setTitle("Edit Server Favorit")
                .setView(layout)
                .setPositiveButton("Simpan", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String ip = etIp.getText().toString().trim();
                    String pass = etPass.getText().toString();
                    int port = item.getPort();
                    try {
                        int p = Integer.parseInt(etPort.getText().toString().trim());
                        if (p > 0 && p <= 65535) port = p;
                    } catch (NumberFormatException ignored) {}
                    if (ip.isEmpty()) return;
                    repo.updateFavorite(item.getId(), name.isEmpty() ? ip + ":" + port : name, ip, port, pass);
                    if (onSaved != null) onSaved.run();
                    Toast.makeText(context, "Server diperbarui!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    public static void promptPasswordIfNeeded(Context context, ServerRepository repo, ServerItem server, PasswordResult callback) {
        if (!server.isPassworded() || !server.getPassword().isEmpty()) {
            callback.onConfirmed(server);
            return;
        }
        String saved = repo.getSavedPassword(server.getIp(), server.getPort());
        if (!saved.isEmpty()) {
            server.setPassword(saved);
            callback.onConfirmed(server);
            return;
        }

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 8);
        layout.setBackgroundColor(Color.parseColor("#0F172A"));

        EditText etPass = new EditText(context);
        etPass.setHint("Masukkan kata sandi server...");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setTextColor(Color.WHITE);
        layout.addView(etPass);

        CheckBox cbSave = new CheckBox(context);
        cbSave.setText("Simpan sandi untuk server ini");
        cbSave.setTextColor(Color.WHITE);
        cbSave.setChecked(true);
        layout.addView(cbSave);

        new AlertDialog.Builder(context)
                .setTitle("Server Dilindungi Kata Sandi")
                .setMessage(server.getName() + " (" + server.getAddress() + ") meminta kata sandi.")
                .setView(layout)
                .setPositiveButton("Masuk & Main", (d, w) -> {
                    String pw = etPass.getText().toString();
                    server.setPassword(pw);
                    if (cbSave.isChecked()) repo.setSavedPassword(server.getIp(), server.getPort(), pw);
                    callback.onConfirmed(server);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    public static void shareJoinLink(Context context, ServerItem server) {
        if (server == null) return;
        String joinUrl = "romp://connect?host=" + server.getIp() + "&port=" + server.getPort()
                + "&name=" + android.net.Uri.encode(server.getName());
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ROMP Join Link", joinUrl));
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Gabung Server " + server.getName());
        send.putExtra(Intent.EXTRA_TEXT, "Ayo main di " + server.getName() + " lewat ROMP by HARFA!\n" + joinUrl);
        context.startActivity(Intent.createChooser(send, "Bagikan Join Link Server"));
    }
}

