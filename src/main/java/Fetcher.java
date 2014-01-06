import com.ning.http.client.AsyncHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.Sets.newHashSet;
import static java.text.MessageFormat.format;

/**
 * Created by Roman on 12/29/13.
 */
public class Fetcher {

    private final String noteTemplate = "\n{p} {note} {/p}";

    private XPathFactory xpathFactory = XPathFactory.newInstance();
    private DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    private final Properties properties;
    private String url;
    private String[] stakes;
    private String notesFile;
    private int[] labels;
    private Set<String> ignorePlayers = newHashSet();

    public Fetcher(Properties properties) {

        this.url = (String) properties.get("url");
        this.stakes = ((String) properties.get("stakes")).split(",");
        this.notesFile = (String) properties.get("notes");
        this.labels = new int[4];

        labels[0] = Integer.parseInt((String) properties.get("label.clueless"));
        labels[1] = Integer.parseInt((String) properties.get("label.very_bad"));
        labels[2] = Integer.parseInt((String) properties.get("label.loser"));
        labels[3] = Integer.parseInt((String) properties.get("label.winner"));

        ignorePlayers.addAll(Arrays.asList(((String) properties.get("ignore.players")).split(",")));

        this.properties = properties;

    }

    public Document executeRequest(String url) throws Exception {
        AsyncHttpClient client = new AsyncHttpClient();
        String body = client.prepareGet(url).execute().get().getResponseBody();
        return Jsoup.parse(body);
    }

    public void startPolling() throws Exception {

        org.w3c.dom.Document xml = parseXml();
        int updated = 0, inserted = 0, not_touched = 0;

        for (String stakeEntry : stakes) {
            String[] stake = stakeEntry.split(":");
            System.out.println(format("Polling for NL{0} {1}-max", stake[0], stake[1]));
            Document doc = executeRequest(url.replace("{stake}", stake[0]).replace("{players}", stake[1]));
            Elements names = doc.select("tr");
            System.out.println("found " + names.size() + " players");
            for (Element entry : names) {
                String name = entry.select("td a").text().trim();
                if (isNullOrEmpty(name) || ignorePlayers.contains(name))
                    continue;

                Elements stats = entry.select("td.color_emphasis");
                int hands = parseHands(stats.get(1));

                if (hands < 2000) // not enough hands to make a statement
                    continue;

                Float winRate = Float.parseFloat(stats.get(3).text().trim());

                int label = assingLabel(winRate);

                String note = format("H: {0} /VP: {1} /PFR: {2} /3B: {3} /rate: {4} /profit: {5}",
                        add(stats.get(1)), add(stats.get(5)), add(stats.get(6)), add(stats.get(7)), add(stats.get(3)), add(stats.get(2)));

                System.out.println(name + " : " + note);

                try {
                    int action = addPlayerNote(name, note, label, xml);
                    switch (action) {
                        case 1:
                            inserted++;
                            break;
                        case 2:
                            updated++;
                            break;
                        case 0:
                            not_touched++;
                    }
                } catch (Exception ex) {
                    System.out.println("could not add note for player : " + name);
                }


            }

        }
        safeXml(xml);

        System.out.println();
        System.out.println(format("notes updated/created/not touched {0}/{1}/{2}", updated, inserted, not_touched));

    }

    private int parseHands(Element element) {
        return Integer.parseInt(element.text().trim().replaceFirst("\\.[0-9]k", "000").replaceFirst("\\.[0-9]+M", "000000").replace("k", "000").replace("M", "000000"));
    }

    private int addPlayerNote(String name, String note, int label, org.w3c.dom.Document xml) throws Exception {

        XPath xpath = xpathFactory.newXPath();
        Node node = (Node) xpath.evaluate("/notes/note[@player='" + name + "']", xml, XPathConstants.NODE);

        String newNote = noteTemplate.replace("{note}", note);
        if (node != null) {
            if (node.getTextContent().contains("{p}"))
                if (!node.getTextContent().contains(newNote))
                    node.setTextContent(node.getTextContent().replaceFirst("\n\\{p\\}.*\\{/p\\}", newNote));
                else
                    return 0;
            else
                node.setTextContent(node.getTextContent() + newNote);
            return 2;
        } else {
            // create a new node
            Node notes = xml.getFirstChild();
            org.w3c.dom.Element noteElement = xml.createElement("note");
            noteElement.setAttribute("player", name);
            noteElement.setAttribute("label", String.valueOf(label));
            String time = String.valueOf(new Date().getTime());
            noteElement.setAttribute("update", time.substring(0, time.length() - 3));
            noteElement.setTextContent(newNote);
            notes.appendChild(noteElement);
            return 1;
        }
    }


    private int assingLabel(Float winrate) {
        // this section can be edited by you
        if (winrate < -12.0) {
            return labels[0];
        } else if (winrate < -7.0) {
            return labels[1];
        } else if (winrate < -2.0) {
            return labels[2];
        } else if (winrate > 1.0) {
            return labels[3];
        } else
            return -1;
    }


    private void safeXml(org.w3c.dom.Document document) throws TransformerException {
        DOMSource source = new DOMSource(document);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StreamResult result = new StreamResult(notesFile);
        transformer.transform(source, result);
    }

    private org.w3c.dom.Document parseXml() throws ParserConfigurationException, SAXException, IOException {
        InputSource source = new InputSource(new FileInputStream(notesFile));
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(source);
    }

    private String add(Element el) {
        return el.text().trim();
    }


    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.load(new FileReader("zoomFetcher.properties"));
        final Fetcher fetcher = new Fetcher(properties);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    fetcher.startPolling();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 15, TimeUnit.MINUTES);


    }
}
