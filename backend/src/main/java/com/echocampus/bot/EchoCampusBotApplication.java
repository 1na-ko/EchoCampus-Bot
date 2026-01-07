package com.echocampus.bot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * EchoCampus-Bot 启动类
 * 基于RAG技术的智能校园问答系统
 *
 * @author EchoCampus Team
 */
@SpringBootApplication
@MapperScan("com.echocampus.bot.mapper")
@EnableAsync
public class EchoCampusBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoCampusBotApplication.class, args);
        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════╗
            ║                                                       ║
            ║   ███████╗ ██████╗██╗  ██╗ ██████╗                   ║
            ║   ██╔════╝██╔════╝██║  ██║██╔═══██╗                  ║
            ║   █████╗  ██║     ███████║██║   ██║                  ║
            ║   ██╔══╝  ██║     ██╔══██║██║   ██║                  ║
            ║   ███████╗╚██████╗██║  ██║╚██████╔╝                  ║
            ║   ╚══════╝ ╚═════╝╚═╝  ╚═╝ ╚═════╝                   ║
            ║                                                       ║
            ║   EchoCampus-Bot 智能校园问答系统 启动成功!           ║
            ║                                                       ║
            ║   API文档: http://localhost:8080/api/doc.html        ║
            ║   Druid监控: http://localhost:8080/api/druid         ║
            ║                                                       ║
            ╚═══════════════════════════════════════════════════════╝
            """);
    }
}
