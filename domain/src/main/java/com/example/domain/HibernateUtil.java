package com.example.domain;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Утилитарный класс для создания и хранения SessionFactory (Singleton).
 */
public class HibernateUtil {
    private static final Logger log = LoggerFactory.getLogger(HibernateUtil.class);

    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        try {
            log.debug("Building SessionFactory using hibernate.cfg.xml...");
            Configuration cfg = new Configuration();
            cfg.configure("hibernate.cfg.xml");
            SessionFactory factory = cfg.buildSessionFactory();
            log.info("SessionFactory created successfully");
            return factory;
        } catch (Exception e) {
            log.error("Error building SessionFactory", e);
            throw new RuntimeException("Failed to build SessionFactory", e);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}