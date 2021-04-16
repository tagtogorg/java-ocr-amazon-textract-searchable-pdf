import java.io.File;

public class DemoTagtogOcr {
    public static void main(String args[]) {
        try {
            String inputFilepath = args[0];

            File outputFile = File.createTempFile(inputFilepath, ".pdf");
            String outputFilepath = outputFile.getAbsolutePath();

            // Generate OCR'ed PDF from input file
            if (inputFilepath.toLowerCase().endsWith(".pdf")) {
                DemoPdfFromLocalPdf localPdf = new DemoPdfFromLocalPdf();
                localPdf.run(inputFilepath, outputFilepath);
            }
            else {
                DemoPdfFromLocalImage localImage = new DemoPdfFromLocalImage();
                localImage.run(inputFilepath, outputFilepath);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
