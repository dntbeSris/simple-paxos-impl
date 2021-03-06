package com.example.paxos.process;

import com.example.paxos.bean.Phase;
import com.example.paxos.bean.common.Message;
import com.example.paxos.bean.paxos.*;
import com.example.paxos.exception.OperatorUnSupportException;
import com.example.paxos.proxy.NodeProxy;
import com.example.paxos.util.BeanFactory;
import com.example.paxos.util.ConstansAndUtils;
import com.example.paxos.util.LogUtil;
import org.springframework.http.HttpEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.AsyncRestTemplate;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class ProposalProcess implements Runnable{

    private static final AtomicInteger numberCounter = new AtomicInteger(0);

    private static final ProposedStatus CURRENT_PROPOSED_STATUS = new ProposedStatus();

    public static volatile boolean needSendProposal = false;

    private final AsyncRestTemplate asyncRestTemplate = BeanFactory.getBean(AsyncRestTemplate.class);

    public ProposalProcess(){}

    @Override
    public void run() {
        if(!needSendProposal){
            //PROPOSAL_PROCESS_LOGGER.info("don't need send proposal now");
            return;
        }
        final NodeProxy nodeProxy = NodeProxy.NodeProxyInstance.INSTANCE.getInstance();
        //current node is a proposor
        if(!nodeProxy.getLocalServer().getRole().isProposer()){
            throw new OperatorUnSupportException("this role:" + Arrays.toString(nodeProxy.getLocalServer().getRole().getRoleTypes().toArray()) + "cannot proposal vote");
        }
        Proposal proposal = new Proposal();
        //global increment number
        proposal.setNumber(nodeProxy.getLocalServer().getNodeNumber() + numberCounter.incrementAndGet());
        //the single value in cluter is ip,choosen the value like electron a leader
        proposal.setContent(nodeProxy.getLocalServer().getIp());
        proposal.setVoteFrom(nodeProxy.getLocalServer().getIp());
        // acceptors had choosened a value
        if(!StringUtils.isEmpty(nodeProxy.choosenedValue())){
            proposal.setContent(nodeProxy.choosenedValue());
        }
        CURRENT_PROPOSED_STATUS.setLastSendedNumber(proposal.getNumber());
        CURRENT_PROPOSED_STATUS.setLastSendedValue(proposal.getContent());

        //parallel send proposal to majority acceptors
        nodeProxy.getMojorityAcceptors().parallelStream().forEach( acceptor -> {
            HttpEntity<Message> httpEntity = new HttpEntity<>(new Message.MessageBuilder<Proposal>()
                    .setT(proposal)
                    .setCode(Phase.PREPARE.getCode())
                    .setMsg(Phase.PREPARE.getPhase()).build());
            asyncRestTemplate.postForEntity(ConstansAndUtils.HTTP_PREFIXX + acceptor.getIp() + ConstansAndUtils.PORT + ConstansAndUtils.API_COMMAND_PREPARE_SEND_PROPOSAL,
                    httpEntity,Message.class)
                    .addCallback((success)->{
                        LogUtil.info("PREPARE: send proposal to acceptors success");
                    },(error)->{
                        LogUtil.error("PREPARE: send proposal to acceptors fail");
                    });
        });
    }


    private Integer currentProposalNumber(){
        return NodeProxy.NodeProxyInstance.INSTANCE.getInstance().getLocalServer().getNodeNumber() + numberCounter.get();
    }

    public static ProposedStatus getCurrentProposedStatus() {
        return CURRENT_PROPOSED_STATUS;
    }
}