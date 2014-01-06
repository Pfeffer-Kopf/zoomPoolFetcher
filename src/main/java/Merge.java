import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
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
import java.util.Date;
import java.util.Properties;

/**
 * Created by Roman on 12/29/13.
 */
public class Merge {

    private XPathFactory xpathFactory = XPathFactory.newInstance();
    private DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();


    private Document parseXml(String xml) throws ParserConfigurationException, SAXException, IOException {
        InputSource source = new InputSource(new FileInputStream(xml));
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(source);
    }

    private void safeXml(org.w3c.dom.Document document, String target) throws TransformerException {
        DOMSource source = new DOMSource(document);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StreamResult result = new StreamResult(target);
        transformer.transform(source, result);
    }

    public void merge(String source, String target) throws IOException, SAXException, ParserConfigurationException, XPathExpressionException, TransformerException {

        Document originalDoc = parseXml(source);
        Document targetDoc = parseXml(target);
        int merged = 0;
        int inserted = 0;

        NodeList childNodes = originalDoc.getFirstChild().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeName().equals("note") && item.getTextContent().contains("Zoom")) {
                try {
                    XPath xpath = xpathFactory.newXPath();
                    String name = item.getAttributes().getNamedItem("player").getNodeValue();
                    String label = item.getAttributes().getNamedItem("label").getNodeValue();
                    Node node = (Node) xpath.evaluate("/notes/note[@player='" + name + "']", targetDoc, XPathConstants.NODE);
                    String message = item.getTextContent().replaceFirst("\n\\{p\\}.*\\{/p\\}", "");
                    if (node != null) {
                        node.setTextContent(node.getTextContent() + "\n" + message);
                        merged++;
                        System.out.println("merged note for " + name);
                    } else {
                        Node notes = targetDoc.getFirstChild();
                        org.w3c.dom.Element noteElement = targetDoc.createElement("note");
                        noteElement.setAttribute("player", name);
                        noteElement.setAttribute("label", String.valueOf(label));
                        String time = String.valueOf(new Date().getTime());
                        noteElement.setAttribute("update", time.substring(0, time.length() - 3));
                        noteElement.setTextContent(message);
                        notes.appendChild(noteElement);
                        inserted++;
                        System.out.println("inserted note for " + name);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        safeXml(targetDoc, target);

        System.out.println("inserted , merged " + inserted + " : " + merged);
    }


    public static void main(String[] args) throws Exception {

        Properties properties = new Properties();
        properties.load(new FileReader("zoomFetcher.properties"));
        Merge merge = new Merge();
        merge.merge((String) properties.get("merge.source"), (String) properties.get("merge.target"));
    }


}
