package com.example.domain;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ItemRepository {

    private static final Logger log = LoggerFactory.getLogger(ItemRepository.class);

    public Item findById(Long id) {
        log.debug("findById called with id={}", id);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Item item = session.get(Item.class, id);
            if (item == null) {
                log.warn("Item with id={} not found", id);
            } else {
                log.debug("Item with id={} found: {}", id, item);
            }
            return item;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Item> findAll() {
        log.debug("findAll called");
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Item> items = session.createQuery("from Item").list();
            log.debug("Total items found: {}", items.size());
            return items;
        }
    }

    public void save(Item item) {
        log.debug("save called with item={}", item);
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(item);
            tx.commit();
            log.info("Item saved successfully with id={}", item.getId());
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Error while saving item", e);
            throw e;
        }
    }

    public void update(Item item) {
        log.debug("update called with item={}", item);
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.merge(item);
            tx.commit();
            log.info("Item updated successfully with id={}", item.getId());
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Error while updating item", e);
            throw e;
        }
    }

    public void delete(Long id) {
        log.debug("delete called with id={}", id);
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Item item = session.get(Item.class, id);
            if (item != null) {
                session.remove(item);
                log.info("Item deleted with id={}", id);
            } else {
                log.warn("Item with id={} not found for deletion", id);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            log.error("Error while deleting item id={}", id, e);
            throw e;
        }
    }
}