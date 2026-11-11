package es.storeapp.business.repositories;

import es.storeapp.business.entities.Product;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository extends AbstractRepository<Product> {

    public List<Product> findByCategory(String category) {
        return entityManager.createQuery(
                "SELECT p FROM Product p "
                        + "WHERE p.category.name = :category "
                        + "ORDER BY p.price",
                Product.class)
                .setParameter("category", category)
                .getResultList();
    }
}
