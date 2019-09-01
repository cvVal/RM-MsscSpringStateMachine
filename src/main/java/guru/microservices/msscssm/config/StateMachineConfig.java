package guru.microservices.msscssm.config;

import guru.microservices.msscssm.domain.PaymentEvent;
import guru.microservices.msscssm.domain.PaymentState;
import guru.microservices.msscssm.services.PaymentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;
import java.util.Random;

@Slf4j
@Configuration
@EnableStateMachineFactory
public class StateMachineConfig extends StateMachineConfigurerAdapter<PaymentState, PaymentEvent> {

    private static final String PRE_AUTH_CALL = "PreAuth was called!!";
    private static final String APPROVED = "Approved";
    private static final String DECLINED = "Declined, No credit available";
    private static final String AUTH_CALL = "Auth was called!!";
    private static final String AUTH_APPROVED = "Auth Approved";
    private static final String AUTH_DECLINED = "Auth declined, No credit available";

    @Override
    public void configure(StateMachineStateConfigurer<PaymentState, PaymentEvent> states) throws Exception {
        states.withStates()
                .initial(PaymentState.NEW)
                .states(EnumSet.allOf(PaymentState.class))
                .end(PaymentState.AUTH)
                .end(PaymentState.PRE_AUTH_ERROR)
                .end(PaymentState.AUTH_ERROR);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<PaymentState, PaymentEvent> transitions) throws Exception {
        transitions
                .withExternal().source(PaymentState.NEW).target(PaymentState.NEW).event(PaymentEvent.PRE_AUTHORIZE)
                    .action(preAuthAction()).guard(paymentIdGuard())
                .and()
                .withExternal().source(PaymentState.NEW).target(PaymentState.PRE_AUTH).event(PaymentEvent.PRE_AUTH_APPROVED)
                .and()
                .withExternal().source(PaymentState.NEW).target(PaymentState.PRE_AUTH_ERROR).event(PaymentEvent.PRE_AUTH_DECLINED)
                .and()
                .withExternal().source(PaymentState.PRE_AUTH).target(PaymentState.PRE_AUTH).event(PaymentEvent.AUTHORIZE)
                    .action(authAction())
                .and()
                .withExternal().source(PaymentState.PRE_AUTH).target(PaymentState.AUTH).event(PaymentEvent.AUTH_APPROVED)
                .and()
                .withExternal().source(PaymentState.PRE_AUTH).target(PaymentState.AUTH_ERROR).event(PaymentEvent.AUTH_DECLINED);

    }

    @Override
    public void configure(StateMachineConfigurationConfigurer<PaymentState, PaymentEvent> config) throws Exception {
        StateMachineListenerAdapter<PaymentState, PaymentEvent> adapter = new StateMachineListenerAdapter<>() {

            @Override
            public void stateChanged(State<PaymentState, PaymentEvent> from, State<PaymentState, PaymentEvent> to) {
                log.info("StateChanged from {} to {}", from, to);
            }
        };

        config
                .withConfiguration()
                .listener(adapter);
    }

    public Guard<PaymentState, PaymentEvent> paymentIdGuard() {

        return context -> {
            return context.getMessageHeader(PaymentServiceImpl.PAYMENT_ID_HEADER) != null;
        };
    }

    public Action<PaymentState, PaymentEvent> preAuthAction() {

        return fillActionMethod(PRE_AUTH_CALL, APPROVED, DECLINED, PaymentEvent.PRE_AUTH_APPROVED, PaymentEvent.PRE_AUTH_DECLINED);
    }

    public Action<PaymentState, PaymentEvent> authAction() {

        return fillActionMethod(AUTH_CALL, AUTH_APPROVED, AUTH_DECLINED, PaymentEvent.AUTH_APPROVED, PaymentEvent.AUTH_DECLINED);
    }

    private Action<PaymentState, PaymentEvent> fillActionMethod(String wasCalledMessage, String approvedMessage,
                                                                String declinedMessage, PaymentEvent eventApproved,
                                                                PaymentEvent eventDeclined) {
        return context -> {
            System.out.println(wasCalledMessage);

            if (new Random().nextInt(10) < 8) {
                System.out.println(approvedMessage);
                context.getStateMachine().sendEvent(MessageBuilder.withPayload(eventApproved)
                        .setHeader(PaymentServiceImpl.PAYMENT_ID_HEADER, context.getMessageHeader(PaymentServiceImpl.PAYMENT_ID_HEADER))
                        .build());
            } else {
                System.out.println(declinedMessage);
                context.getStateMachine().sendEvent(MessageBuilder.withPayload(eventDeclined)
                        .setHeader(PaymentServiceImpl.PAYMENT_ID_HEADER, context.getMessageHeader(PaymentServiceImpl.PAYMENT_ID_HEADER))
                        .build());
            }
        };
    }
}