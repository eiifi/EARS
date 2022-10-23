import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class FileService {


    Logger log = Logger.getLogger(FileService.class.getName());

    @Inject
    MessageProducer messageProducer;

    public void saveDataStream(DataStream dataStream, String ROOT_PATH, String TYPE_FILE) {
        //TODO: poskrbi za enako oddano datoteko
        validateFileName(dataStream.getFileName(), TYPE_FILE);

        try {
            writeFile(dataStream.getData(), ROOT_PATH + dataStream.getFileName());
        } catch (IOException e) {
            log.info(e.getMessage());
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
        messageProducer.sendMessage(dataStream.getFileName(), Status.UPLOADED);
        log.info("File saved: " + dataStream.getFileName());
    }

    private void validateFileName(String fileName, String TYPE_FILE) {
        if (fileName == null || fileName.trim().isEmpty()) {
            log.info("Incorrect or empty name of file: " + fileName);
            throw new WebApplicationException("Incorrect or empty name of file: " + fileName, Response.Status.BAD_REQUEST);
        }
        if (!fileName.endsWith(TYPE_FILE)) {
            log.info("ONLY " + TYPE_FILE + " FILES ALLOWED");
            throw new WebApplicationException("ONLY " + TYPE_FILE + " FILES ALLOWED", Response.Status.BAD_REQUEST);
        }
        String tmp = fileName.replace(TYPE_FILE, "");

        Pattern p = Pattern.compile("[^A-Za-z]");
        Matcher m = p.matcher(tmp);
        if (m.find()) {
            log.info("SPECIAL CHARTERS INCLUDED: " + tmp);
            throw new WebApplicationException("SPECIAL CHARTERS INCLUDED IN NAME: " + tmp + ". Only allowed A-Z, a-z", Response.Status.BAD_REQUEST);
        }
    }

    private void writeFile(byte[] content, String filename) throws IOException {

        File file = new File(filename);

        if (!file.exists()) {
            file.createNewFile();
        }

        FileOutputStream fop = new FileOutputStream(file);

        fop.write(content);
        fop.flush();
        fop.close();

    }

}
