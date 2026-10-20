package com.captchafox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A siteverify on the loopback interface that records every request it receives. */
final class LocalSiteverify implements AutoCloseable {

    static final class Received {
        final String method;
        final String contentType;
        final Map<String, String> form;

        Received(String method, String contentType, Map<String, String> form) {
            this.method = method;
            this.contentType = contentType;
            this.form = form;
        }
    }

    private enum Mode { ANSWER, SILENT, STALL, TRICKLE }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Received> requests = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch closed = new CountDownLatch(1);
    private volatile long brokenAtNanos;

    private LocalSiteverify(Mode mode, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/siteverify", exchange -> {
            // HttpExchange is AutoCloseable only from Java 18 on.
            try {
                requests.add(receive(exchange));
                switch (mode) {
                    case ANSWER:
                        answer(exchange, status, body);
                        break;
                    case SILENT:
                        awaitClose();
                        break;
                    case STALL:
                        sendPart(exchange, status, body);
                        awaitClose();
                        break;
                    case TRICKLE:
                        sendPart(exchange, status, body);
                        trickle(exchange);
                        break;
                    default:
                        throw new IllegalStateException("unknown mode " + mode);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    static LocalSiteverify answering(int status, String body) throws IOException {
        return new LocalSiteverify(Mode.ANSWER, status, body);
    }

    /** Receives requests and never answers them while it runs. */
    static LocalSiteverify silent() throws IOException {
        return new LocalSiteverify(Mode.SILENT, 0, null);
    }

    /** Sends the status, the headers and the start of a body, then nothing more while it runs. */
    static LocalSiteverify stalling(int status, String start) throws IOException {
        return new LocalSiteverify(Mode.STALL, status, start);
    }

    /** Sends the status, the headers and the start of a body, then a space every 100 ms until the client goes. */
    static LocalSiteverify trickling(int status, String start) throws IOException {
        return new LocalSiteverify(Mode.TRICKLE, status, start);
    }

    /** When a trickling server found the connection closed, by {@link System#nanoTime()}; 0 while it is open. */
    long brokenAtNanos() {
        return brokenAtNanos;
    }

    /** An endpoint at a port of the loopback interface where nothing listens. */
    static URI refusingEndpoint() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        return URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port + "/siteverify");
    }

    URI endpoint() {
        InetSocketAddress address = server.getAddress();
        return URI.create("http://" + address.getAddress().getHostAddress() + ":" + address.getPort() + "/siteverify");
    }

    List<Received> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    @Override
    public void close() {
        closed.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    private static Received receive(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, String> form = new LinkedHashMap<>();
        if (!body.isEmpty()) {
            for (String pair : body.split("&", -1)) {
                int separator = pair.indexOf('=');
                String name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                if (form.put(name, value) != null) {
                    throw new IllegalStateException("the field " + name + " was sent twice");
                }
            }
        }
        return new Received(exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Content-Type"), form);
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        // -1 sends no body; 0 would mean a chunked body.
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendPart(HttpExchange exchange, int status, String start) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        // 0 sends a chunked body, which has no end until the stream is closed.
        exchange.sendResponseHeaders(status, 0);
        exchange.getResponseBody().write(start.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    private void trickle(HttpExchange exchange) {
        try {
            while (!closed.await(100, TimeUnit.MILLISECONDS)) {
                exchange.getResponseBody().write(' ');
                exchange.getResponseBody().flush();
            }
        } catch (IOException e) {
            brokenAtNanos = System.nanoTime();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitClose() {
        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
