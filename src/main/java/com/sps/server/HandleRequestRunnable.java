package com.sps.server;

import com.sps.server.model.Request;
import com.sps.server.model.Response;
import com.sps.server.model.StatusCode;
import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Optional;

@RequiredArgsConstructor
public class HandleRequestRunnable implements Runnable {
    private final Socket client;
    private final boolean httpAuthEnabled;
    private final String httpAuthUser;
    private final String httpAuthPassword;
    private final File rootFile;

    @Override
    public void run() {
        try {
            try (InputStream is = client.getInputStream(); OutputStream os = client.getOutputStream()) {
                try {
                    Optional<Request> request = Request.from(is);
                    if (request.isPresent()) {
                        Response response = handleRequest(request.get());
                        response.writeTo(os);
                    }
                    // else: empty request..
                } catch (ParseException e) {
                    e.printStackTrace();
                    new Response(e.getStatus(), null).writeTo(os);
                } catch (Exception e) {
                    e.printStackTrace();
                    new Response(StatusCode.INTERNAL_ERROR, null).writeTo(os);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (client != null) {
                    client.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Response handleRequest(Request request) throws IOException {
        String auth = request.readHeaders().get("authorization");
        if (isAuthed(auth)) {
            return handleAuthedRequest(request);
        }
        Response response = new Response(StatusCode.UNAUTHORIZED, null);
        response.addHeader("WWW-authenticate", "Basic realm=\"entire site\"");
        return response;
    }

    private boolean isAuthed(String authRaw) throws IOException {
        if (!httpAuthEnabled) {
            return true;
        }
        if (authRaw == null) {
            return false;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(authRaw.substring("Basic ".length()));
        } catch (Exception e) {
            throw new ParseException(StatusCode.BAD_REQUEST, "invalid auth header");
        }
        String userAndPass = new String(decoded, Charset.forName("UTF-8"));
        int colonIndex = userAndPass.indexOf(':');
        if (colonIndex == -1) {
            throw new ParseException(StatusCode.BAD_REQUEST, "invalid auth header");
        }
        String user = userAndPass.substring(0, colonIndex);
        String password = userAndPass.substring(colonIndex + 1);
        return httpAuthUser.equals(user) && httpAuthPassword.equals(password);
    }

    private Response handleAuthedRequest(Request request) throws IOException {
        switch (request.getMethod()) {
            case GET:
                return handleReadRequest(request);
            case POST:
                return handleCommandRequest(request);
            default:
                return new Response(StatusCode.BAD_REQUEST, "unsupported method".getBytes());
        }
    }

    private Response handleReadRequest(Request request) throws IOException {
        String url = request.getUrl();
        //System.out.format("%s: %s\n", request.getMethod(), url);
        int queryIndex = url.indexOf("?");
        if (queryIndex != -1 ) {
            url = url.substring(0, queryIndex);
        }
        if (url.endsWith("/")) {
            url += "index.html";
        }

        Optional<byte[]> file = readFile(url);
        if (file.isPresent()) {
            return new Response(StatusCode.OK, file.get());
        } else {
            return new Response(StatusCode.NOT_FOUND, null);
        }
    }

    private Response handleCommandRequest(Request request) throws IOException {
        try {
            JSONObject body = new JSONObject(new String(request.readBody()));
            String command = body.getString("command");
            switch (command) {
                case "edit": {
                    String content = body.getString("content");
                    String filename = body.getString("filename");
                    writeFile(filename, content);
                    return new Response(StatusCode.OK, null);
                }
                case "create": {
                    String filename = body.getString("filename");
                    return createFile(filename);
                }
                case "list": {
                    return listFiles();
                }
                case "delete": {
                    return deleteFile(body.getString("filename"));
                }
                default:
                    return new Response(StatusCode.BAD_REQUEST, "unknown command".getBytes());
            }

        } catch (JSONException e) {
            throw new ParseException(StatusCode.BAD_REQUEST, "Body could not be parsed as json");
        }
    }

    private Optional<byte[]> readFile(String filename) throws IOException {
        Optional<File> file = getFile(filename);
        if (file.isPresent()) {
            return Optional.of(Files.readAllBytes(file.get().toPath()));
        }
        return Optional.empty();
    }

    private void writeFile(String filename, String content) throws IOException {
        Optional<File> file = getFile(filename);
        if (!file.isPresent()) {
            throw new RuntimeException("could not find file " + filename);
        }

        try (FileOutputStream fos = new FileOutputStream(file.get())) {
            fos.write(content.getBytes());
            fos.close();
        }
    }

    private Response createFile(String filename) throws IOException {
        File file = new File(rootFile, filename);
        if (!isChild(rootFile, file)) {
            return new Response(StatusCode.BAD_REQUEST, "May not write to location".getBytes());
        }
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    return new Response(StatusCode.INTERNAL_ERROR, "failed to create".getBytes());
                }
            }
            if (!file.createNewFile()) {
                return new Response(StatusCode.INTERNAL_ERROR, "failed to create".getBytes());

            }
        }
        return new Response(StatusCode.OK, null);
    }

    private Optional<File> getFile(String filename) {
        File file = new File(rootFile, filename);
        if (!(isChild(rootFile, file) && file.exists())) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private boolean isChild(File parent, File child) {
        return child.getAbsolutePath().startsWith(parent.getAbsolutePath());
    }

    private Response deleteFile(String filename) {
        Optional<File> file = getFile(filename);
        if (file.isPresent()) {
            if (file.get().delete()) {
                return new Response(StatusCode.OK, null);
            }
            return new Response(StatusCode.INTERNAL_ERROR, "failed to delete".getBytes());
        }
        return new Response(StatusCode.NOT_FOUND, null);
    }

    private Response listFiles() {
        try {
            JSONObject listing = listFilesRecursive(rootFile);
            JSONObject response = new JSONObject();
            response.put("listing", listing);
            return new Response(StatusCode.OK, response.toString().getBytes());
        } catch (JSONException e) {
            return new Response(StatusCode.INTERNAL_ERROR, "failed to list files".getBytes());
        }
    }

    private JSONObject listFilesRecursive(File me) throws JSONException {
        JSONObject listing = new JSONObject();
        for (File child : me.listFiles()) {
            Object childListing = null;
            if (child.isDirectory()) {
                childListing = listFilesRecursive(child);
            } else {
                childListing = JSONObject.NULL;
            }
            listing.put(child.getName(), childListing);
        }
        return listing;
    }

//    private Response respondHeadersForTesting(Request request) throws IOException {
//        StringBuilder b = new StringBuilder();
//        b.append("method:");
//        b.append(request.getMethod());
//        b.append("<br/>");
//        b.append("url:");
//        b.append(request.getUrl());
//        b.append("<br/>");
//        b.append("version:");
//        b.append(request.getVersion());
//        b.append("<br/>");
//        b.append("<br/>");
//        for (Map.Entry<String, String> header : request.readHeaders().entrySet()) {
//            b.append(header.getKey());
//            b.append(":");
//            b.append(header.getValue());
//            b.append("<br/>");
//        }
//
//        return new Response(StatusCode.OK, b.toString().getBytes());
//    }
}
