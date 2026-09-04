package com.xt.xiaoxingxing.playground.features.basics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IoNioDemo {

    private IoNioDemo() {
    }

    public static void main(String[] args) throws IOException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("java-basics-", ".txt");
            Files.writeString(tempFile, "Java NIO 文件读写", StandardCharsets.UTF_8);

            String content = Files.readString(tempFile, StandardCharsets.UTF_8);
            System.out.println("临时文件：" + tempFile);
            System.out.println("读取内容：" + content);
        } finally {
            // 即使读写失败，也尝试清理临时文件。
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
                System.out.println("文件已清理：" + !Files.exists(tempFile));
            }
        }
    }
}
