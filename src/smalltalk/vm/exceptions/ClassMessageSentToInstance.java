package smalltalk.vm.exceptions;

/**
 * Created by xyz on 4/30/15.
 */
public class ClassMessageSentToInstance extends VMException {
      public ClassMessageSentToInstance(String message, String vmStackTrace) {
            super(message, vmStackTrace);
      }
}
