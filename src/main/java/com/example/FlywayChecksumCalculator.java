package com.example;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;

public class FlywayChecksumCalculator {

    private static boolean verbose = false;

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("Usage: java FlywayChecksumCalculator <src_file> [dst_file] [--verbose]");
                return;
            }

            String srcFile = args[0];
            String dstFile = null;

            for (int i = 1; i < args.length; i++) {
                if ("--verbose".equals(args[i])) {
                    verbose = true;
                } else if (dstFile == null) {
                    dstFile = args[i];
                }
            }

            if (dstFile == null) {
                calculateAndDisplayChecksum(srcFile);
                return;
            }

            int srcChecksum = calculateChecksum(srcFile);
            int dstChecksum = calculateChecksum(dstFile);

            System.out.println("=== FlyWay Checksum Calculator ===");
            System.out.println("Source file: " + srcFile);
            System.out.println("  Checksum: " + srcChecksum + " (0x" + Integer.toHexString(srcChecksum).toUpperCase() + ")");
            System.out.println();
            System.out.println("Target file: " + dstFile);
            System.out.println("  Checksum: " + dstChecksum + " (0x" + Integer.toHexString(dstChecksum).toUpperCase() + ")");
            System.out.println();

            if (srcChecksum == dstChecksum) {
                System.out.println("✅ All OK! Checksums match!");
                return;
            }

            System.out.println("Checksums differ. Starting brute-force to find matching comment...");
            System.out.println();

            System.out.println("=== Brute-forcing Printable Comment ===");
            System.out.println("Target CRC32: " + srcChecksum + " (0x" + Integer.toHexString(srcChecksum).toUpperCase() + ")");
            System.out.println("Searching for comment string '--<chars>' that produces target checksum...");
            System.out.println();

            String matchingComment = bruteForceCommentDirect(dstFile, srcChecksum);

            if (matchingComment != null) {
                System.out.println("✅ Found printable comment: '" + matchingComment + "'");
                System.out.println();

                System.out.println("=== Verifying Solution ===");
                String dstContent = new String(Files.readAllBytes(Paths.get(dstFile)), java.nio.charset.StandardCharsets.UTF_8);

                String trimmedContent = dstContent.replaceAll("\\n+$", "");
                String modifiedContent = trimmedContent + "\n" + matchingComment + "\n";

                int verificationChecksum = calculateChecksumForContent(modifiedContent);

                System.out.println("Modified content checksum: " + verificationChecksum + " (0x" + Integer.toHexString(verificationChecksum).toUpperCase() + ")");
                System.out.println("Target checksum:          " + srcChecksum + " (0x" + Integer.toHexString(srcChecksum).toUpperCase() + ")");
                System.out.println();

                if (verificationChecksum == srcChecksum) {
                    System.out.println("✅ Verification passed! Saving modified file...");
                    System.out.println();

                    String backupPath = dstFile + ".old";
                    Files.move(Paths.get(dstFile), Paths.get(backupPath));
                    System.out.println("Original file backed up to: " + backupPath);

                    Files.write(Paths.get(dstFile), modifiedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    System.out.println("Modified file saved to: " + dstFile);
                    System.out.println();
                    System.out.println("SUCCESS! File has been updated with matching comment.");
                } else {
                    System.out.println("❌ Verification failed! Checksums don't match.");
                    System.out.println("Not saving the file.");
                }
            } else {
                System.out.println("❌ Could not find printable comment within search limits");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String bruteForceCommentDirect(String dstFile, int targetChecksum) throws Exception {
        String dstContent = new String(Files.readAllBytes(Paths.get(dstFile)), java.nio.charset.StandardCharsets.UTF_8);

        CRC32 baseCrc = new CRC32();
        String[] lines = dstContent.split("\n");
        for (String line : lines) {
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            byte[] bytes = line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baseCrc.update(bytes);
        }

        int baseValue = (int) baseCrc.getValue();

        if (verbose) {
            System.out.println("Base CRC32: " + baseValue + " (0x" + Integer.toHexString(baseValue).toUpperCase() + ")");
        }

        String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz !@#$%^&*()-_=+[]{}|;:',.<>?/`~";

        for (int commentLength = 1; commentLength <= 8; commentLength++) {
            System.out.println("Trying comment length: " + commentLength);
            long startTime = System.currentTimeMillis();

            int NUM_THREADS = 16;
            ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
            AtomicBoolean found = new AtomicBoolean(false);
            AtomicReference<String> result = new AtomicReference<>(null);

            List<Future<?>> futures = new ArrayList<>();

            int charsPerThread = Math.max(1, CHARSET.length() / NUM_THREADS);

            for (int t = 0; t < NUM_THREADS; t++) {
                final int startIdx = t * charsPerThread;
                final int endIdx = (t == NUM_THREADS - 1) ? CHARSET.length() : (t + 1) * charsPerThread;
                final int length = commentLength;

                Future<?> future = executor.submit(() -> {
                    try {
                        String foundComment = searchCommentDirect(
                            CHARSET, startIdx, endIdx, "", length,
                            baseValue, targetChecksum, found
                        );
                        if (foundComment != null) {
                            result.set(foundComment);
                        }
                    } catch (Exception e) {
                        if (verbose) {
                            System.err.println("Thread error: " + e.getMessage());
                        }
                    }
                });

                futures.add(future);
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                }
            }

            executor.shutdown();

            long elapsed = System.currentTimeMillis() - startTime;

            if (result.get() != null) {
                System.out.println("✅ Found in " + elapsed + " ms");
                return result.get();
            }

            System.out.println("   Not found at length " + commentLength + " (" + elapsed + " ms)");
        }

        return null;
    }

    private static String searchCommentDirect(String charset, int startIdx, int endIdx,
                                              String current, int targetLength,
                                              int baseValue, int targetCrc,
                                              AtomicBoolean found) {
        Field crcField = null;
        try {
            crcField = CRC32.class.getDeclaredField("crc");
            crcField.setAccessible(true);
        } catch (Exception e) {
            System.err.println("❌ Failed to access CRC32 field: " + e.getMessage());
            return null;
        }

        CRC32 testCrc = new CRC32();

        return searchCommentRecursiveDirect(charset, startIdx, endIdx, current, targetLength,
                                           baseValue, targetCrc, found, testCrc, crcField);
    }

    private static String searchCommentRecursiveDirect(String charset, int startIdx, int endIdx,
                                                       String current, int targetLength,
                                                       int baseValue, int targetCrc,
                                                       AtomicBoolean found,
                                                       CRC32 testCrc, Field crcField) {
        if (found.get()) {
            return null;
        }

        if (current.length() == targetLength) {
            String fullComment = "--" + current;

            try {
                crcField.setInt(testCrc, baseValue);

                testCrc.update(fullComment.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                if ((int) testCrc.getValue() == targetCrc) {
                    found.set(true);
                    return fullComment;
                }
            } catch (Exception e) {
            }

            return null;
        }

        int start = (current.isEmpty()) ? startIdx : 0;
        int end = (current.isEmpty()) ? endIdx : charset.length();

        for (int i = start; i < end && !found.get(); i++) {
            String result = searchCommentRecursiveDirect(
                charset, startIdx, endIdx,
                current + charset.charAt(i),
                targetLength, baseValue, targetCrc,
                found, testCrc, crcField
            );

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private static void calculateAndDisplayChecksum(String filePath) throws Exception {
        int checksum = calculateChecksum(filePath);

        System.out.println("Source file: " + filePath);
        System.out.println();
        System.out.println("=== Calculating Checksum ===");
        System.out.println("Checksum (FlyWay): " + checksum);
        System.out.println("Checksum (hex): 0x" + Integer.toHexString(checksum).toUpperCase());

        try {
            long fileSize = Files.size(Paths.get(filePath));
            System.out.println("File size: " + fileSize + " bytes");
        } catch (IOException e) {
        }

        System.out.println();
        System.out.println("✅ Checksum calculated successfully!");
    }

    public static int calculateChecksum(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)), java.nio.charset.StandardCharsets.UTF_8);
        return calculateChecksumForContent(content);
    }

    private static int calculateChecksumForContent(String content) {
        CRC32 crc32 = new CRC32();

        String[] lines = content.split("\n");

        for (String line : lines) {
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }

            byte[] bytes = line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            crc32.update(bytes);
        }

        return (int) crc32.getValue();
    }
}

