package com.tvguard.app;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

// May khach ADB toi gian: TV tu ket noi vao adbd cua chinh no (127.0.0.1:5555) de chay lenh shell
// (vd "reboot -p" = tat nguon that). App thuong khong co quyen tat nguon, nhung shell ADB thi co.
// Can bat "Gỡ lỗi ADB qua mạng" tren TV (cung dieu kien de ban da dung "adb connect IP:5555")
// va bam "Luon cho phep" 1 lan khi TV hien hop thoai xin quyen (nut "Kiem tra quyen tat nguon" trong app TV).
final class AdbLocal {
    private static final int A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f,
            A_OKAY = 0x59414b4f, A_CLSE = 0x45534c43, A_WRTE = 0x45545257;
    private static final int VERSION = 0x01000001, MAXDATA = 256 * 1024;
    // DigestInfo(SHA-1) - adb ky truc tiep len "token" 20 byte coi nhu da la ma bam SHA-1
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    private final Socket sock;
    private final DataInputStream in;
    private final OutputStream out;

    private AdbLocal(Socket s) throws IOException {
        sock = s;
        in = new DataInputStream(s.getInputStream());
        out = s.getOutputStream();
    }

    // Chay 1 lenh shell, tra ve ket qua in ra. Lenh nhu "reboot -p" lam ngat ket noi dot ngot -> van coi la thanh cong.
    static String exec(File keyDir, int port, String cmd) throws Exception {
        KeyPair kp = loadKey(keyDir);
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            s.setTcpNoDelay(true);
            s.setSoTimeout(60000); // co the phai cho nguoi dung bam "Cho phep" tren TV
            AdbLocal a = new AdbLocal(s);
            a.handshake(kp);
            s.setSoTimeout(15000);
            return a.shell(cmd);
        } finally {
            try { s.close(); } catch (Exception e) { }
        }
    }

    static String explain(Exception e) {
        if (e instanceof ConnectException)
            return "ADB qua mang chua bat (cong 5555) - bat \"Go loi ADB\" trong Tuy chon nha phat trien cua TV";
        if (e instanceof SocketTimeoutException)
            return "het thoi gian cho - hay bam \"Luon cho phep\" tren TV khi hop thoai hien ra, roi kiem tra lai";
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    // ---------- giao thuc ADB ----------
    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        for (int i = 0; i < len; i++) sum += data[i] & 0xff;
        ByteBuffer h = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(len).putInt(sum).putInt(cmd ^ 0xffffffff);
        if (len > 0) h.put(data);
        out.write(h.array());
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] hd = new byte[24];
        in.readFully(hd);
        ByteBuffer b = ByteBuffer.wrap(hd).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > MAXDATA) throw new IOException("goi ADB sai kich thuoc");
        m.data = new byte[len];
        if (len > 0) in.readFully(m.data);
        return m;
    }

    private void handshake(KeyPair kp) throws Exception {
        send(A_CNXN, VERSION, MAXDATA, "host::\0".getBytes(StandardCharsets.UTF_8));
        boolean signed = false, keySent = false;
        while (true) {
            Msg m = read();
            if (m.cmd == A_CNXN) return;
            if (m.cmd == A_AUTH && m.arg0 == 1) {
                if (!signed) {
                    send(A_AUTH, 2, 0, sign(kp.getPrivate(), m.data));
                    signed = true;
                } else if (!keySent) {
                    send(A_AUTH, 3, 0, publicKeyBlob(kp));
                    keySent = true;
                } else {
                    throw new IOException("TV tu choi khoa ADB (da bam Tu choi?)");
                }
            } else {
                throw new IOException("goi ADB la: 0x" + Integer.toHexString(m.cmd));
            }
        }
    }

    private String shell(String cmd) throws IOException {
        send(A_OPEN, 1, 0, ("shell:" + cmd + "\0").getBytes(StandardCharsets.UTF_8));
        Msg m = read();
        if (m.cmd != A_OKAY) throw new IOException("ADB khong mo duoc shell");
        int remote = m.arg0;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try {
            while (true) {
                m = read();
                if (m.cmd == A_WRTE) {
                    o.write(m.data);
                    send(A_OKAY, 1, remote, null);
                } else if (m.cmd == A_CLSE) {
                    break;
                }
            }
        } catch (IOException e) {
            // ngat ket noi / het gio doc: lenh tat nguon thuong lam adbd dung ngay
        }
        return new String(o.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    // ---------- khoa RSA ----------
    private static KeyPair loadKey(File dir) throws Exception {
        File f = new File(dir, "adbkey.pk8");
        if (f.exists()) {
            try {
                PrivateKey pk = KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(f.toPath())));
                if (pk instanceof RSAPrivateCrtKey) return new KeyPair(null, pk);
            } catch (Exception e) {
                // file hong -> sinh khoa moi
            }
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(kp.getPrivate().getEncoded());
        fo.close();
        return kp;
    }

    private static byte[] sign(PrivateKey k, byte[] token) throws Exception {
        byte[] d = new byte[SHA1_PREFIX.length + token.length];
        System.arraycopy(SHA1_PREFIX, 0, d, 0, SHA1_PREFIX.length);
        System.arraycopy(token, 0, d, SHA1_PREFIX.length, token.length);
        Signature sg = Signature.getInstance("NONEwithRSA");
        sg.initSign(k);
        sg.update(d);
        return sg.sign();
    }

    // Dinh dang khoa cong khai kieu Android (mincrypt RSAPublicKey) roi base64
    static byte[] publicKeyBlob(KeyPair kp) {
        RSAPrivateCrtKey k = (RSAPrivateCrtKey) kp.getPrivate();
        BigInteger n = k.getModulus(), e = k.getPublicExponent();
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        int n0inv = two32.subtract(n.mod(two32).modInverse(two32)).intValue();
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt(n0inv);
        b.put(le(n, 256));
        b.put(le(BigInteger.ONE.shiftLeft(4096).mod(n), 256));
        b.putInt(e.intValue());
        String s = Base64.getEncoder().encodeToString(b.array()) + " tvguard@tv\0";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] le(BigInteger v, int size) {
        byte[] be = v.toByteArray();
        byte[] r = new byte[size];
        for (int i = 0; i < size && i < be.length; i++) r[i] = be[be.length - 1 - i];
        return r;
    }
}
