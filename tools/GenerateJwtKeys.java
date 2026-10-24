import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class GenerateJwtKeys {

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: java tools/GenerateJwtKeys.java [directory]");
        }
        var directory = Path.of(args.length == 0 ? "secrets" : args[0]).toAbsolutePath().normalize();
        var privatePath = directory.resolve("private.pem");
        var publicPath = directory.resolve("public.pem");
        if (Files.exists(privatePath) || Files.exists(publicPath)) {
            throw new IllegalStateException("Key files already exist; existing keys will not be overwritten");
        }
        Files.createDirectories(directory);
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        var privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(privatePath, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(privatePath, privatePem, StandardCharsets.US_ASCII);
        } else {
            Files.writeString(privatePath, privatePem, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        }
        Files.writeString(publicPath, pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        System.out.println("RSA key pair created in " + directory);
    }

    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }
}
