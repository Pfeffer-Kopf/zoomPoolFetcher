import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Created by roman on 4/7/14.
 */
public class Main {

    public static void main(String[] args) {

        LocalDate date = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("MMM d HH:mm:ss");
        String str = date.toString(fmt);
        System.out.println(str);

    }
}
