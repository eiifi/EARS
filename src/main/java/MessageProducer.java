import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSRuntimeException;
import javax.jms.Session;
import java.util.logging.Logger;

@ApplicationScoped
public class MessageProducer {

    Logger log = Logger.getLogger(ProcessorService.class.getName());

    @Inject
    ConnectionFactory connectionFactory;

    @ConfigProperty(name = "mq.enabled")
    protected Boolean mq_enabled;

    public void sendMessage(String message, Status status) {
        if(mq_enabled) {
            try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)){
                context.createProducer().send(context.createQueue("statusQueue"), message + "_" + status);
            } catch (JMSRuntimeException ex) {
                log.severe(ex.getMessage());
            }
        }
    }


}
