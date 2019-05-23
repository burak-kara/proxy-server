import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ProxyHandler implements HttpHandler {

    /*
        Problems:
        * POST requests have problems
        * Images and other get requests to different destinations
    */

    private String url;
    private char key; // TODO
    private boolean loadPage;

    @Override
    public void handle(HttpExchange exchange) {
        URI requestUri = exchange.getRequestURI();
        String query = requestUri.getQuery();

        if (query != null && query.startsWith("link")) {
                key = query.charAt(query.indexOf("=") + 1);
                url = query.substring(query.indexOf("=") + 2);
                loadPage = true;
        }

        String targetAddress = url;
        if (!loadPage)
            targetAddress += requestUri.toString();
        loadPage = false;

        //System.out.println("target address: " + targetAddress);

        try {
            URL targetUrl = new URL(targetAddress);

            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod(exchange.getRequestMethod());
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            // Add headers to target connection
            addHeadersToHttpRequest(exchange.getRequestHeaders(), connection);

            int length = connection.getResponseCode() == 204 ? -1 : 0;
            exchange.sendResponseHeaders(connection.getResponseCode(), length);

            if (exchange.getRequestBody().available() > 0) {
                System.out.println("+++++++++++++++++++++++++++++++++++++++++++++++++++++++");
                if (!connection.getDoOutput())
                    connection.setDoOutput(true);

                transferStream(exchange.getRequestBody(), connection.getOutputStream());
            }

            if (key == 'y' && isImage(targetAddress)) {
                System.out.println("*********************************************************");
                compressImage(connection.getInputStream(), exchange.getResponseBody());
            } else if (connection.getInputStream().available() > 0) {
                System.out.println("-------------------------------------------------------");
                transferStream(connection.getInputStream(), exchange.getResponseBody());
            }

            connection.disconnect();

            exchange.close();
        } catch (IOException error) {
            printRequestInfo(exchange);
            System.out.println(error.toString());
        }
    }

    private boolean isImage(String targetAddress) {
        return targetAddress.contains("jpg") || targetAddress.contains("jpeg") || targetAddress.contains("png");
    }

    public void addHeadersToHttpRequest(Headers headers, HttpURLConnection connection) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String property = entry.getKey();
            if (property.toLowerCase().equals("accept-encoding")) {
                continue;
            }

            List<String> valueList = entry.getValue();

            if (valueList.size() == 0)
                continue;

            String values = valueList.get(0);

            for (int i = 1; i < valueList.size(); i++)
                values += ", " + valueList.get(i);

            connection.setRequestProperty(property, values);
        }
    }

    public void transferStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) >= 0)
            if (read > 0)
                outputStream.write(buffer, 0, read);
        outputStream.flush();
    }

    public void printRequestInfo(HttpExchange exchange) {
        System.out.println("--- HTTP Package ---");

        // Print request
        String requestMethod = exchange.getRequestMethod()
                + " " + exchange.getRequestURI().toString()
                + " " + exchange.getProtocol();

        System.out.println(requestMethod);

        // Print headers
        Headers requestHeaders = exchange.getRequestHeaders();
        requestHeaders.entrySet().forEach(System.out::println);
        System.out.println("\n");
    }

    private void compressImage(InputStream is, OutputStream os) {
        try {
            float quality = 0.5f;

            // create a BufferedImage as the result of decoding the supplied InputStream
            BufferedImage image = ImageIO.read(is);

            // get all image writers for JPG format
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

            if (!writers.hasNext())
                throw new IllegalStateException("No writers found");

            ImageWriter writer = writers.next();
            ImageOutputStream ios = ImageIO.createImageOutputStream(os);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();

            // compress to a given quality
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            // appends a complete image stream containing a single image and
            //associated stream and image metadata and thumbnails to the output
            writer.write(null, new IIOImage(image, null, null), param);

            // close all streams
            is.close();
            os.flush();
            ios.close();
            writer.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}