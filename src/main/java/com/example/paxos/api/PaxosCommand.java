package com.example.paxos.api;

import com.alibaba.fastjson.TypeReference;
import com.example.paxos.bean.Phase;
import com.example.paxos.bean.common.Message;
import com.example.paxos.bean.paxos.Proposal;
import com.example.paxos.core.PaxosCore;
import com.example.paxos.exception.UnKnowPhaseException;
import com.example.paxos.util.ConstansAndUtils;
import com.example.paxos.util.LogUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;

@RestController
public class PaxosCommand extends BaseServerRequest{

    @PostMapping(value = ConstansAndUtils.API_COMMAND_PREPARE_SEND_PROPOSAL)
    public Message sendProposal(@RequestBody String jsonString, HttpServletResponse response) {
        Message<Proposal> message = parser(jsonString,new TypeReference<Message<Proposal>>(){});
        if(Message.isEmpty(message)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        Phase phase = null;
        try {
            phase = Phase.getPahse(message.getMessage(),message.getCode());
            if(!phase.equals(Phase.PREPARE)){
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
        }catch (Exception e){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        PaxosCore.reviceProposalFromProposor(phase,message);
        return new Message.MessageBuilder<>().setCode(200).build();
    }


    @PostMapping(value = ConstansAndUtils.API_COMMAND_PREPARE_REPLY_PROPOSAL)
    public Message replyProposal(@RequestBody String jsonString, HttpServletResponse response){
        Message<Proposal> message = parser(jsonString,new TypeReference<Message<Proposal>>(){});
        if(Message.isEmpty(message)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        Object proposal = message.getT();
        Phase phase = null;
        try {
            phase = Phase.getPahse(message.getMessage(),message.getCode());
        }catch (UnKnowPhaseException e){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        if(proposal instanceof Proposal){
            PaxosCore.reviceProposalFromAcceptor(phase,message);
        }
        return new Message.MessageBuilder<>().setCode(200).build();
    }


    @PostMapping(value = ConstansAndUtils.API_COMMAND_APPROVED_SEND_PROPOSAL)
    public Message approvedSendProposal(@RequestBody String jsonString, HttpServletResponse response){
        Message<Proposal> message = parser(jsonString,new TypeReference<Message<Proposal>>(){});
        if(Message.isEmpty(message)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        Phase phase = null;
        try {
            phase = Phase.getPahse(message.getMessage(),message.getCode());
            if(!phase.equals(Phase.APPROVE)){
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
        }catch (UnKnowPhaseException e){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        PaxosCore.reviceProposalFromProposor(phase,message);
        return new Message.MessageBuilder<>().setCode(200).build();
    }


    @PostMapping(value = ConstansAndUtils.API_COMMAND_APPROVED_REPLY_CHOSENED_VALUE)
    public Message replyChosenValue(@RequestBody String jsonString, HttpServletResponse response){
        Message<Proposal> message = parser(jsonString,new TypeReference<Message<Proposal>>(){});
        if(Message.isEmpty(message)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        return new Message.MessageBuilder<>().setCode(200).build();
    }


    @PostMapping(value = ConstansAndUtils.API_COMMAND_APPROVED_LEARNING)
    public Message learning(@RequestBody String jsonString, HttpServletResponse response){
        Message<Proposal> message = parser(jsonString,new TypeReference<Message<Proposal>>(){});
        if(Message.isEmpty(message)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        Phase phase = null;
        try {
            phase = Phase.getPahse(message.getMessage(),message.getCode());
            if(!phase.equals(Phase.LEARNING)){
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return null;
            }
        }catch (UnKnowPhaseException e){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        if(!(message.getT() instanceof  Proposal)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        Proposal proposal = (Proposal) message.getT();
        PaxosCore.learning(proposal);
        return new Message.MessageBuilder<>().setCode(200).build();
    }

}