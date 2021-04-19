import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;;

public class DemoTagtogOcr {

    private static DemoPdfFromLocalPdf localPdf = new DemoPdfFromLocalPdf();
    private static DemoPdfFromLocalImage localImage = new DemoPdfFromLocalImage();

    private static String tagtogDomain = System.getenv("TAGTOG_DOMAIN");
    private static String tagtogUsername = System.getenv("TAGTOG_USERNAME");
    private static String tagtogPassword = System.getenv("TAGTOG_PASSWORD");

    private static String tagtogProjectOwner;
    private static String tagtogProjectName;
    private static String tagtogProjectFolder;

    public static void main(String args[]) {
        try {
            tagtogProjectOwner = args[0];
            tagtogProjectName = args[1];
            tagtogProjectFolder = args[2];

            String inputFilepath = args[3];

            Files.walk(Paths.get(inputFilepath)).filter(p -> {
                return Files.isRegularFile(p)
                        && ACCEPTED_FILES_EXTS.contains(getLowercasedExtension(p.getFileName().toString()));
            }).forEach(p -> {
                File outputFile = doOcr(p.toString());
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void traverseFsTree(String[] inputFilepaths) {

    }

    public static File doOcr(String inputFilepath) {
        OcrProcessor ocrProcessor = chooseOcr(inputFilepath);
        File outputFile = null;

        try {
            outputFile = File.createTempFile("tagtog-sample-ocr", ".pdf");
            String outputFilepath = outputFile.getAbsolutePath();

            ocrProcessor.run(inputFilepath, outputFilepath);

            return outputFile;
        } catch (Exception e) {
            if (outputFile != null && outputFile.exists()) {
                outputFile.delete();
            }

            throw new RuntimeException(e);
        }
    }

    private static final Set<String> ACCEPTED_FILES_EXTS = Set.of("pdf", "png", "jpg", "jpeg");

    public static OcrProcessor chooseOcr(String inputFilepath) {
        OcrProcessor ocrProcessor;
        String extension = getLowercasedExtension(inputFilepath);

        switch (extension) {
        case "pdf":
            ocrProcessor = localPdf;
            break;
        default:
            ocrProcessor = localImage;
        }

        return ocrProcessor;
    }

    public static String getLowercasedExtension(String filepath) {
        String extension = "";

        int i = filepath.lastIndexOf('.');
        if (i > 0) {
            extension = filepath.substring(i + 1);
        }

        return extension.toLowerCase();
    }

    // https://stackoverflow.com/questions/9093237/setting-custom-file-name-in-apache-httpclient
    // https://stackoverflow.com/questions/6917105/java-http-client-to-upload-file-over-post
    // https://mkyong.com/java/apache-httpclient-examples/
    // https://eu-west-1.console.aws.amazon.com/textract/home?region=eu-west-1#/demo
    // https://docs.aws.amazon.com/textract/latest/dg/getting-started.html
}
