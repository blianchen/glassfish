package test;

import java.io.*;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.transaction.UserTransaction;

public class TestServlet extends HttpServlet {
    @Resource(mappedName = "jms/jms_unit_test_Queue")
    private Queue queue;

    @Inject
    @JMSConnectionFactory("jms/jms_unit_test_QCF")
    @JMSSessionMode(JMSContext.AUTO_ACKNOWLEDGE)
    private JMSContext jmsContext;

    @Inject
    UserTransaction ut;

    private static String transactionScope = "around TransactionScoped";

    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        boolean success = true;
        String context;
        try {
            ut.begin();
            JMSProducer producer = jmsContext.createProducer();
            TextMessage msg = jmsContext.createTextMessage("Hello Servlet");
            producer.send(queue, msg);
            context = jmsContext.toString();
            ut.commit();
            if(context.indexOf(transactionScope) == -1){
                throw new ServletException("NOT in transaction scope!");
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }

        res.getWriter().print(success);
    }
}
