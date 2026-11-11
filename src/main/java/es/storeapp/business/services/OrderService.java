package es.storeapp.business.services;

import es.storeapp.business.entities.Order;
import es.storeapp.business.entities.OrderLine;
import es.storeapp.business.entities.OrderState;
import es.storeapp.business.entities.Product;
import es.storeapp.business.entities.User;
import es.storeapp.business.exceptions.InstanceNotFoundException;
import es.storeapp.business.exceptions.InvalidStateException;
import es.storeapp.business.repositories.OrderLineRepository;
import es.storeapp.business.repositories.OrderRepository;
import es.storeapp.business.repositories.ProductRepository;
import es.storeapp.business.repositories.UserRepository;
import es.storeapp.business.utils.ExceptionGenerationUtils;
import es.storeapp.common.Constants;
import java.text.MessageFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderLineRepository orderLineRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    ExceptionGenerationUtils exceptionGenerationUtils;
    
    @Transactional()
    public Order create(User user, String name, String address, List<Long> products)
            throws InstanceNotFoundException {
        Order order = new Order();
        order.setName(name);
        order.setUser(user);
        order.setAddress(address != null ? address : user.getAddress());
        order.setState(OrderState.PENDING);
        order.setTimestamp(System.currentTimeMillis());
        order.setPrice(0);
        orderRepository.create(order);

        int totalPrice = 0;
        for (Long productId : products) {
            Product product = productRepository.findById(productId);
            product.setSales(product.getSales() + 1);
            OrderLine orderLine = new OrderLine();
            orderLine.setPrice(product.getPrice());
            totalPrice += product.getPrice();
            orderLine.setProduct(product);
            orderLine.setOrder(order);  
            orderLineRepository.create(orderLine);
        }
        order.setPrice(totalPrice);

        return orderRepository.findById(order.getOrderId());
    }
    
    @Transactional()
    public Order pay(User user, Long orderId, boolean useSavedCard, String cardNumber, boolean saveCard)
        throws InstanceNotFoundException, InvalidStateException {
        Order order = findById(orderId, user.getUserId());
        if(order.getState() != OrderState.PENDING) {
            logger.warn("Trying to pay non-pending order {}", orderId);
            throw exceptionGenerationUtils.toInvalidStateException(Constants.INVALID_STATE_EXCEPTION_MESSAGE);
        }
        if (useSavedCard ? userRepository.findById(user.getUserId()).getSavedCardLastFour() == null
                         : cardNumber == null || !cardNumber.matches("[0-9]{13,19}")) {
            throw exceptionGenerationUtils.toInvalidStateException(Constants.PAY_ORDER_INVALID_PARAMS_MESSAGE);
        }
        // Payments are simulated in this lab. Only a display label is kept for future purchases.
        if (!useSavedCard && saveCard) {
            String lastFour = cardNumber.substring(cardNumber.length() - 4);
            userRepository.updateSavedCardLastFour(user.getUserId(), lastFour);
            user.setSavedCardLastFour(lastFour);
        }
        order.setState(OrderState.COMPLETED);
        return orderRepository.update(order);
    }
    
    @Transactional()
    public Order cancel(User user, Long orderId) 
        throws InstanceNotFoundException, InvalidStateException {
        Order order = findById(orderId, user.getUserId());
        if(order.getState() != OrderState.PENDING) {
            throw exceptionGenerationUtils.toInvalidStateException(Constants.INVALID_STATE_EXCEPTION_MESSAGE);
        }
        order.setState(OrderState.CANCELLED);
        return orderRepository.update(order);
    }
    
    @Transactional(readOnly = true)
    public List<Order> findByUserById(Long userId) throws InstanceNotFoundException {
        User user = userRepository.findById(userId);
        if(logger.isDebugEnabled()) {
            logger.debug(MessageFormat.format("Searching the orders of the user {0}", user.getEmail()));
        }
        return orderRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Order findById(Long orderId, Long userId) throws InstanceNotFoundException {
        Order order = orderRepository.findById(orderId);
        if (!order.getUser().getUserId().equals(userId)){
            throw exceptionGenerationUtils.toInstanceNotFoundException(
                    orderId, Order.class.getSimpleName(), Constants.INSTANCE_NOT_FOUND_MESSAGE);
        }
        return order;
    }

    @Transactional(readOnly = true)
    public boolean findIfUserBuyProduct(Long userId, Long productId) throws InstanceNotFoundException {
        User user = userRepository.findById(userId);
        if(logger.isDebugEnabled()) {
            logger.debug(MessageFormat.format("Checking if user {0} buy the product {1}", 
                user.getEmail(), productId));
        }
        return orderLineRepository.findIfUserBuyProduct(userId, productId);
    }
    
}
