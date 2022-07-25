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
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

// https://eu-west-1.console.aws.amazon.com/textract/home?region=eu-west-1#/demo
// https://docs.aws.amazon.com/textract/latest/dg/getting-started.html
// https://github.com/aws-samples/amazon-textract-searchable-pdf
public class DemoTagtogOcr {

    private static final String TAGTOG_DOMAIN = Optional.ofNullable(System.getenv("TAGTOG_DOMAIN"))
            .orElse("https://www.tagtog.com");
    private static final String TAGTOG_USERNAME = Optional.ofNullable(System.getenv("TAGTOG_USERNAME"))
            .orElseThrow(() -> new IllegalArgumentException("You must define the envar: TAGTOG_USERNAME"));
    private static final String TAGTOG_PASSWORD = Optional.ofNullable(System.getenv("TAGTOG_PASSWORD"))
            .orElseThrow(() -> new IllegalArgumentException("You must define the envar: TAGTOG_PASSWORD"));

    // -------------------------------------------------------------------------

    private static String ttProjectOwner;
    private static String ttProjectName;
    private static String ttProjectFolder;

    private static CloseableHttpClient httpClient = null;

    // -------------------------------------------------------------------------

    private static final String DOCS_API_URL = TAGTOG_DOMAIN + "/-api/documents/v1";

    private static final Set<String> ACCEPTED_FILES_EXTS = Set.of("pdf", "png", "jpg", "jpeg");

    private static final Credentials credentials = new UsernamePasswordCredentials(TAGTOG_USERNAME, TAGTOG_PASSWORD);

    // https://github.com/apache/httpcomponents-client/blob/4.5.x/httpclient/src/examples/org/apache/http/examples/client/ClientAuthentication.java
    private static final CredentialsProvider credsProvider = new BasicCredentialsProvider();
    static {
        credsProvider.setCredentials(AuthScope.ANY, credentials);
    };

    // -------------------------------------------------------------------------

    private static final DemoPdfFromLocalPdf localPdf = new DemoPdfFromLocalPdf();
    private static final DemoPdfFromLocalImage localImage = new DemoPdfFromLocalImage();

    // -------------------------------------------------------------------------

    public static void main(String args[]) throws Exception {
        try {
            initHttpClient();

            testTagtogCredentials();

            ttProjectOwner = args[0];
            ttProjectName = args[1];
            ttProjectFolder = args[2];

            for (int i = 3; i < args.length; i++) {
                String inputFilepath = args[i];
                traverseTreeAndApply(inputFilepath, DemoTagtogOcr::doOcrAndUploadToTagtog);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (httpClient != null) {
                httpClient.close();
            }
        }
    }

    // -------------------------------------------------------------------------

    public static void doOcrAndUploadToTagtog(Path inputPath) {
        File outputTmpFile = null;
        try {
            outputTmpFile = doOcr(inputPath.toString()); // Hardcode path if needed when testing

            uploadToTagtog(outputTmpFile, maybeAddPdfExtension(inputPath));

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (outputTmpFile != null) {
                outputTmpFile.delete();
            }
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

    /**
     * Add ".pdf" extension to non PDF files (e.g. images like .png).
     *
     * @param inputPath
     * @return
     */
    public static String maybeAddPdfExtension(Path inputPath) {
        String inputFilename = inputPath.getFileName().toString();
        String ext = getLowercasedExtension(inputFilename);
        if (ext != "pdf") {
            inputFilename += ".pdf";
        }
        return inputFilename;
    }

    /**
     * References:
     * https://stackoverflow.com/questions/6917105/java-http-client-to-upload-file-over-post
     * https://stackoverflow.com/questions/9093237/setting-custom-file-name-in-apache-httpclient
     * https://stackoverflow.com/questions/9907161/commons-httpclient-adding-query-string-parameters-to-get-post-request
     *
     * @param file            file to upload to tagtog
     * @param desiredFilename filename that will be sent to tagtog
     */
    public static void uploadToTagtog(File file, String desiredFilename)
            throws URISyntaxException, IOException, AuthenticationException {
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

        try (CloseableHttpResponse response = httpClient.execute(doPreemptiveBasicAuth(post))) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            System.out.println(response.getStatusLine() + " " + responseBody);
        }
    }

    public static void testTagtogCredentials() throws URISyntaxException, IOException, AuthenticationException {
        String userUrl = TAGTOG_DOMAIN + "/" + TAGTOG_USERNAME;

        try (CloseableHttpResponse response = httpClient.execute(doPreemptiveBasicAuth(new HttpGet(userUrl)))) {
            // Toggle when debugging
            // Stream.of(response.getAllHeaders()).forEach(x -> System.out.println(x));
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200) {
                throw new IllegalArgumentException(
                        "The authentication credentials seem wrong. Received " + statusCode + " on " + userUrl);
            }
        }
    }

    public static void traverseTreeAndApply(String inputFilepath, Consumer<Path> applyFun) throws IOException {
        Files.walk(Paths.get(inputFilepath)).filter(p -> {
            return Files.isRegularFile(p)
                    && ACCEPTED_FILES_EXTS.contains(getLowercasedExtension(p.getFileName().toString()));
        }).forEach(p -> {
            applyFun.accept(p);
        });
    }

    // -------------------------------------------------------------------------

    /**
     * Choose the right OcrProcessor depending on the input file's extension.
     *
     * NOTE: this function assumes to receive compatible files only; @see
     * ACCEPTED_FILES_EXTS.
     *
     * @param inputFilepath
     * @return
     */
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

    // -------------------------------------------------------------------------

    private static void initHttpClient() throws Exception {
        httpClient = HttpClients.custom() //
                .setDefaultCredentialsProvider(credsProvider) //
                .setSSLContext(
                        new SSLContextBuilder().loadTrustMaterial(null, TrustSelfSignedStrategy.INSTANCE).build()) //
                // .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
    }

    /**
     * We do preemptive authentication for tagtog did not support pre-emptive
     * authenticaton until 2021-04-25.
     *
     * This can be safely removed in a future version.
     *
     * References:
     * https://github.com/apache/httpcomponents-client/blob/4.5.x/httpclient/src/examples/org/apache/http/examples/client/ClientPreemptiveBasicAuthentication.java
     * https://stackoverflow.com/questions/7482523/preemptive-authentication-why
     * https://stackoverflow.com/a/4328694
     *
     * @param httpRequest
     * @return httpRequest with basic authentication credentials header
     * @throws AuthenticationException
     * @deprecated remove in future version
     */
    private static HttpUriRequest doPreemptiveBasicAuth(HttpUriRequest httpRequest) throws AuthenticationException {
        httpRequest.addHeader(new BasicScheme().authenticate(credentials, httpRequest));
        return httpRequest;
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
