import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

// https://eu-west-1.console.aws.amazon.com/textract/home?region=eu-west-1#/demo
// https://docs.aws.amazon.com/textract/latest/dg/getting-started.html
// https://github.com/aws-samples/amazon-textract-searchable-pdf
public class DemoTagtogOcr {

    private static DemoPdfFromLocalPdf localPdf = new DemoPdfFromLocalPdf();
    private static DemoPdfFromLocalImage localImage = new DemoPdfFromLocalImage();

    private static String TAGTOG_DOMAIN = Optional.ofNullable(System.getenv("TAGTOG_DOMAIN"))
            .orElse("https://tagtog.net");
    private static String TAGTOG_USERNAME = Optional.ofNullable(System.getenv("TAGTOG_USERNAME"))
            .orElseThrow(() -> new IllegalArgumentException("You must define the envar: TAGTOG_USERNAME"));
    private static String TAGTOG_PASSWORD = Optional.ofNullable(System.getenv("TAGTOG_PASSWORD"))
            .orElseThrow(() -> new IllegalArgumentException("You must define the envar: TAGTOG_PASSWORD"));

    private static CloseableHttpClient httpClient = null;

    private static String ttProjectOwner;
    private static String ttProjectName;
    private static String ttProjectFolder;

    private static String DOCS_API_URL = TAGTOG_DOMAIN + "/-api/documents/v1";

    public static void main(String args[]) throws Exception {
        try {
            // https://github.com/apache/httpcomponents-client/blob/4.5.x/httpclient/src/examples/org/apache/http/examples/client/ClientAuthentication.java
            // https://mkyong.com/java/apache-httpclient-examples/
            // https://github.com/apache/httpcomponents-client/blob/4.5.x/httpclient/src/examples/org/apache/http/examples/client/ClientPreemptiveBasicAuthentication.java
            // https://stackoverflow.com/questions/7482523/preemptive-authentication-why
            CredentialsProvider credsProvider = new BasicCredentialsProvider();

            credsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(TAGTOG_USERNAME, TAGTOG_PASSWORD));

            httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider)
                    .setSSLContext(
                            new SSLContextBuilder().loadTrustMaterial(null, TrustSelfSignedStrategy.INSTANCE).build())
                    .setRedirectStrategy(new LaxRedirectStrategy()).build();

            testCredentials(httpClient);

            ttProjectOwner = args[0];
            ttProjectName = args[1];
            ttProjectFolder = args[2];

            String inputFilepath = args[3];

            traverseTreeAndApply(inputFilepath, DemoTagtogOcr::applyFun);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (httpClient != null) {
                httpClient.close();
            }
        }
    }

    public static void applyFun(Path inputPath) {
        File outputTmpFile = null;
        try {
            outputTmpFile = doOcr(inputPath.toString()); // Hardcode path if needed when testing

            uploadToTagtog(outputTmpFile, inputPath.getFileName().toString());

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (outputTmpFile != null) {
                outputTmpFile.delete();
            }
        }
    }

    public static void traverseTreeAndApply(String inputFilepath, Consumer<Path> applyFun) throws IOException {
        Files.walk(Paths.get(inputFilepath)).filter(p -> {
            return Files.isRegularFile(p)
                    && ACCEPTED_FILES_EXTS.contains(getLowercasedExtension(p.getFileName().toString()));
        }).forEach(p -> {
            applyFun(p);
        });
    }

    public static void testCredentials(CloseableHttpClient httpClient) throws URISyntaxException, IOException {
        String userUrl = TAGTOG_DOMAIN + "/" + TAGTOG_USERNAME;

        try (CloseableHttpResponse response = httpClient.execute(new HttpGet(userUrl))) {
            // Toggle when debugging
            // Stream.of(response.getAllHeaders()).forEach(x -> System.out.println(x));
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                throw new IllegalArgumentException(
                        "The authentication credentials seem wrong. Received " + statusCode + " on " + userUrl);
            }
        }
    }

    /**
     * References:
     * https://stackoverflow.com/questions/6917105/java-http-client-to-upload-file-over-post
     * https://stackoverflow.com/questions/9093237/setting-custom-file-name-in-apache-httpclient
     * https://stackoverflow.com/questions/9907161/commons-httpclient-adding-query-string-parameters-to-get-post-request
     *
     * @param file
     */
    public static void uploadToTagtog(File file, String desiredFilename) throws URISyntaxException, IOException {
        URIBuilder urlBuilder = new URIBuilder(DOCS_API_URL);
        urlBuilder.setParameter("owner", ttProjectOwner) //
                .setParameter("project", ttProjectName) //
                .setParameter("folder", ttProjectFolder) //
                .setParameter("output", "null");

        HttpPost post = new HttpPost(urlBuilder.build());

        System.out.println("POST " + desiredFilename + " to:" + urlBuilder.build());

        HttpEntity entity = MultipartEntityBuilder.create()
                .addPart("files", new FileBody(file, ContentType.APPLICATION_OCTET_STREAM, desiredFilename)).build();
        post.setEntity(entity);

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            System.out.println(response.getStatusLine() + " " + responseBody);
        }
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
}
