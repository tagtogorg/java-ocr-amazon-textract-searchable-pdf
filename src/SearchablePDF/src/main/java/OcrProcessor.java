import java.io.IOException;

public interface OcrProcessor {

  public void run(String documentName, String outputDocumentName) throws IOException;

}
