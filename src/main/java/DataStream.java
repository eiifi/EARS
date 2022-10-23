import javax.ws.rs.FormParam;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

public class DataStream {


    @FormParam("uploadedFile")
    @PartType("application/octet-stream")
    private byte[] data;

    @FormParam("fileName")
    private String fileName;


    public DataStream() {
    }


    public byte[] getData() {
        return data;
    }
    public void setData(byte[] data) {
        this.data = data;
    }


    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}