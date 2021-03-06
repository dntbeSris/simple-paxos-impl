package com.example.paxos.process;

import com.example.paxos.bean.Phase;
import com.example.paxos.bean.common.Message;
import com.example.paxos.bean.paxos.AcceptedStatus;
import com.example.paxos.bean.paxos.Node;
import com.example.paxos.bean.paxos.Proposal;
import com.example.paxos.core.PaxosCore;
import com.example.paxos.exception.MessageUnsupportException;
import com.example.paxos.proxy.NodeProxy;
import com.example.paxos.util.BeanFactory;
import com.example.paxos.util.ConstansAndUtils;
import com.example.paxos.util.LogUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.AsyncRestTemplate;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class AcceptProcess implements Runnable {

    private static final AcceptedStatus CURRENT_ACCEPTED_STATUS = new AcceptedStatus();

    private static final LinkedBlockingQueue<Pair> ACCEPTED_REQUESTS =
            new LinkedBlockingQueue<>();

    final AsyncRestTemplate asyncRestTemplate = BeanFactory.getBean(AsyncRestTemplate.class);

    public AcceptProcess() {
    }


    public static void addRequest(Pair<Phase, Message> action) {
        Message message = action.getValue();
        if (!(message.getT() instanceof Proposal)) {
            LogUtil.error("accpted message is illegal:" + message.getT().getClass().getSimpleName());
            throw new MessageUnsupportException(Thread.currentThread().getName() + "-" +
                    "accpted process:message is illegal:" + message.getT().getClass().getSimpleName());
        }
        Proposal proposal = (Proposal) message.getT();
        // 角色判断
        NodeProxy nodeProxy = NodeProxy.NodeProxyInstance.INSTANCE.getInstance();
        if (!nodeProxy.isProposor(proposal.getVoteFrom())) {
            LogUtil.error("the vote message isn't from a proposal");
            return;
        }
        ACCEPTED_REQUESTS.add(action);
    }

    @Override
    public void run() {

        Node local = NodeProxy.NodeProxyInstance.INSTANCE.getInstance().getLocalServer();
        //current role must be a acceptor
        if (!local.getRole().isAcceptor()) {
            //TODO shutdown the scheduler
            return;
        }

        while (!ACCEPTED_REQUESTS.isEmpty()) {
            try {
                Pair action = ACCEPTED_REQUESTS.take();
                Phase phase = (Phase) action.getKey();
                Message message = (Message) action.getValue();
                Proposal proposal = (Proposal) message.getT();
                //PREPARE 阶段处理逻辑
                if (phase.equals(Phase.PREPARE)) {
                    HttpEntity httpEntity = HttpEntity.EMPTY;
                    Message<Proposal> replyMessage = new Message<>(proposal);
                    // 1. 已经chosen，则返回chosen-value
                    if (CURRENT_ACCEPTED_STATUS.hasChosendValue()) {
                        replyMessage.setMessage(Phase.PREPARE.getPhase());
                        replyMessage.setCode(Phase.PREPARE.getCode());
                        Proposal replyProposal = new Proposal();
                        replyProposal.setContent(CURRENT_ACCEPTED_STATUS.getChosenValue());
                        replyProposal.setVoteFrom(local.getIp());
                        replyProposal.setHasChoosen(true);
                        replyMessage.setT(replyProposal);
                        httpEntity = new HttpEntity<>(replyMessage);
                        //2. check lastAccept，如果不为空，返回上次accept的提案。
                    } else if (CURRENT_ACCEPTED_STATUS.getLastAcceptedValue() != null) {
                        replyMessage.setMessage(Phase.PREPARE.getPhase());
                        replyMessage.setCode(Phase.PREPARE.getCode());
                        Proposal replyProposal = new Proposal();
                        replyProposal.setContent(CURRENT_ACCEPTED_STATUS.getLastAcceptedValue());
                        replyProposal.setVoteFrom(local.getIp());
                        replyProposal.setNumber(CURRENT_ACCEPTED_STATUS.getLastAcceptedNumber());
                        //check number，如果大于lastAcceptedNumber，则更新number为更大的值
                        if (CURRENT_ACCEPTED_STATUS.greaterThanLastProposalNumber(proposal.getNumber())) {
                            updateCurrentAcceptedStatus(null, proposal.getNumber(), false);
                            replyProposal.setNumber(proposal.getNumber());
                        }
                        replyMessage.setT(replyProposal);
                        httpEntity = new HttpEntity<>(replyMessage);
                    } else if (CURRENT_ACCEPTED_STATUS.isFirstAcceptProposal()) {
                        // check lastAccept[number，value]，如果为空，更新lastAccept，即必须接受第一个提案
                        updateCurrentAcceptedStatus(proposal.getContent(), proposal.getNumber(), false);
                        replyMessage.setMessage(Phase.PREPARE.getPhase());
                        replyMessage.setCode(Phase.PREPARE.getCode());
                        Proposal replyProposal = new Proposal();
                        BeanUtils.copyProperties(proposal, replyProposal);
                        replyProposal.setVoteFrom(local.getIp());
                        replyMessage.setT(replyProposal);
                        httpEntity = new HttpEntity<>(replyMessage);
                    }
                    asyncRestTemplate.postForEntity(ConstansAndUtils.HTTP_PREFIXX + proposal.getVoteFrom() + ConstansAndUtils.PORT + ConstansAndUtils.API_COMMAND_PREPARE_REPLY_PROPOSAL,
                            httpEntity, Message.class)
                            .addCallback((success) -> {
                                LogUtil.info("PREPARE: reply proposal to proposor success");
                            }, (error) -> {
                                LogUtil.error("PREPARE: reply proposal to proposor fail");
                            });
                    continue;

                }
                //APPROVE 阶段处理逻辑
                if (phase.equals(Phase.APPROVE)) {
                    //批准提案并返回
                    if (CURRENT_ACCEPTED_STATUS.hasChosendValue() && !CURRENT_ACCEPTED_STATUS.getChosenValue().equals(proposal.getContent())) {
                        LogUtil.error("!!!!!!!!!!!!!!!!!!!!!!!!the value not consistant!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    }
                    updateCurrentAcceptedStatus(proposal.getContent(), proposal.getNumber(), true);
                    PaxosCore.stopSendProposal(((Proposal) message.getT()).getContent());
                    //返回批准的提案
                    proposal.setVoteFrom(local.getIp());
                    HttpEntity<Message> approvedEntity = new HttpEntity<>(new Message.MessageBuilder<Proposal>()
                            .setT(proposal)
                            .setCode(Phase.APPROVE.getCode())
                            .setMsg(Phase.APPROVE.getPhase()).build());
                    asyncRestTemplate.postForEntity(ConstansAndUtils.HTTP_PREFIXX + proposal.getVoteFrom() + ConstansAndUtils.PORT + ConstansAndUtils.API_COMMAND_APPROVED_REPLY_CHOSENED_VALUE,
                            approvedEntity, Message.class)
                            .addCallback((success) -> {
                                LogUtil.info("APPROVED: reply proposal to proposor success");
                            }, (error) -> {
                                LogUtil.error("APPROVED: reply proposal to proposor fail");
                            });

                    //通知其中一个learner学习
                    proposal.setHasChoosen(true);
                    HttpEntity<Message> learnerEntity = new HttpEntity<>(new Message.MessageBuilder<Proposal>()
                            .setT(proposal)
                            .setCode(Phase.LEARNING.getCode())
                            .setMsg(Phase.LEARNING.getPhase()).build());

                    Node[] learners = NodeProxy.NodeProxyInstance.INSTANCE.getInstance().getAllLearner().toArray(new Node[]{});
                    Node learnerRandom = learners[ThreadLocalRandom.current().nextInt(learners.length)];
                    asyncRestTemplate.postForEntity(ConstansAndUtils.HTTP_PREFIXX + learnerRandom.getIp() + ConstansAndUtils.PORT + ConstansAndUtils.API_COMMAND_APPROVED_LEARNING,
                            learnerEntity, Message.class)
                            .addCallback((success) -> {
                                LogUtil.info("LEARNING: send proposal to other learner success");
                            }, (error) -> {
                                LogUtil.error("LEARNING: send proposal to other learner fail");
                            });
                    continue;
                }
            } catch (InterruptedException e) {
                LogUtil.error("take accepted request from queue exception" + e.getMessage());
            }
        }
    }

    private void updateCurrentAcceptedStatus(String value, Integer number, boolean choosen) {
        if (value != null) {
            CURRENT_ACCEPTED_STATUS.setLastAcceptedValue(value);
        }
        if (number != null) {
            CURRENT_ACCEPTED_STATUS.setLastAcceptedNumber(number);
        }
        if (choosen) {
            CURRENT_ACCEPTED_STATUS.setChosenValue(value);
        }
    }
}