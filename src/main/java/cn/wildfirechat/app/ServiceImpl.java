package cn.wildfirechat.app;


import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.*;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.sdk.ChatConfig;
import cn.wildfirechat.sdk.MessageAdmin;
import cn.wildfirechat.sdk.UserAdmin;
import cn.wildfirechat.sdk.model.*;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.result.WxMpUser;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@org.springframework.stereotype.Service
public class ServiceImpl implements Service {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);

    @Value("${admin.admin_id}")
    private String mAdminId;

    @Value("${admin.url}")
    private String mAdminUrl;

    @Value("${admin.secret}")
    private String mAdminSecret;

    @Value("${robot.shadow_prefix}")
    private String mShadowPrefix;

    @Value("${wx.app_id}")
    private String mWXAppId;

    @Value("${wx.secret}")
    private String mWXSecret;

    @Value("${wx.token}")
    private String mWXToken;

    @Value("${wx.aeskey}")
    private String mWXAESKey;

    @Value("${wx.subscribe_welcome}")
    private String mWXWelcome;

    @Value("${robot.callback}")
    private String mRobotCallback;


    private WxMpService wxService;

    @PostConstruct
    private void init() {
        ChatConfig.initAdmin(mAdminUrl,  mAdminSecret);

        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId(mWXAppId); // ????????????????????????appid
        config.setSecret(mWXSecret); // ????????????????????????app corpSecret
        config.setToken(mWXToken); // ????????????????????????token
        config.setAesKey(mWXAESKey); // ????????????????????????EncodingAESKey

        wxService = new WxMpServiceImpl();// ???????????????????????????????????????????????????????????????????????????????????????????????????demo??????
        wxService.setWxMpConfigStorage(config);
    }

    private static Document getDocument(String xmlString) throws ParserConfigurationException,
            IOException, org.xml.sax.SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xmlString.getBytes()));
        Element e = document.getDocumentElement();
        return document;
    }


    @Override
    public void onReceiveWXData(String payload) {
        LOG.info("on receive message {}", payload);

        MessagePayload msgPayload = null;
        String fromUserName = null;
        try {
            Document document = getDocument(payload);
            String toUserName = document.getElementsByTagName("ToUserName").item(0).getTextContent();
            fromUserName = document.getElementsByTagName("FromUserName").item(0).getTextContent();
            Long createTime = Long.parseLong(document.getElementsByTagName("CreateTime").item(0).getTextContent());
            String msgType = document.getElementsByTagName("MsgType").item(0).getTextContent();


            if (msgType.equals("event")) {
                String event = document.getElementsByTagName("Event").item(0).getTextContent();
                if (event.equals("subscribe")) {
                    sendWXTextMsg(fromUserName, mWXWelcome);
                }

                msgPayload = new MessagePayload();
                msgPayload.setType(1);
                msgPayload.setSearchableContent(mWXWelcome);
            } else if(msgType.equals("text")) {
                String content = document.getElementsByTagName("Content").item(0).getTextContent();
                String msgId = document.getElementsByTagName("MsgId").item(0).getTextContent();

                msgPayload = new MessagePayload();
                msgPayload.setType(1);
                msgPayload.setSearchableContent(content);
            } else if(msgType.equals("voice")) {
                String mediaId = document.getElementsByTagName("MediaId").item(0).getTextContent();
                String msgId = document.getElementsByTagName("MsgId").item(0).getTextContent();

                msgPayload = new MessagePayload();
                msgPayload.setType(1);
                msgPayload.setSearchableContent("????????????????????????????????????????????????????????????????????????????????????");
            } else if(msgType.equals("image")) {
                String picUrl = document.getElementsByTagName("PicUrl").item(0).getTextContent();
                String mediaId = document.getElementsByTagName("MediaId").item(0).getTextContent();
                String msgId = document.getElementsByTagName("MsgId").item(0).getTextContent();

                msgPayload = new MessagePayload();
                msgPayload.setType(3);
                msgPayload.setSearchableContent("[??????]");
                msgPayload.setMediaType(1);
                msgPayload.setRemoteMediaUrl(picUrl);
            } else if(msgType.equals("location")) {
                String msgId = document.getElementsByTagName("MsgId").item(0).getTextContent();

                msgPayload = new MessagePayload();
                msgPayload.setType(1);
                msgPayload.setSearchableContent("????????????????????????????????????????????????????????????????????????????????????");
            } else {
                msgPayload = new MessagePayload();
                msgPayload.setType(1);
                msgPayload.setSearchableContent("???????????????:" + msgType + "???????????????????????????????????????????????????");
            }
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }

        if (msgPayload != null && fromUserName != null) {
            String robotId = getRobotId(fromUserName);
            if (!isRobotExist(robotId)) {
                createRobot(fromUserName, robotId);
            }
            sendMessage(robotId, mAdminId, msgPayload);
        }
    }

    private boolean isRobotExist(String robotId) {
        try {
            IMResult<InputOutputUserInfo> imResult = UserAdmin.getUserByUserId(robotId);
            if (imResult != null && imResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                System.out.println("robot: " + robotId + " alread exist!");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void createRobot(String wxId, String robotId) {
        //get user info from wx and create rebot in wildfire
        try {
            WxMpUser mpUser = wxService.getUserService().userInfo(wxId);
            if (mpUser != null) {
                InputCreateRobot inputCreateRobot = new InputCreateRobot();
                inputCreateRobot.setUserId(robotId);
                inputCreateRobot.setDisplayName(mpUser.getNickname());
                inputCreateRobot.setName(robotId);
                inputCreateRobot.setAddress(mpUser.getCountry() + " " + mpUser.getProvince() + " " + mpUser.getCity());
                inputCreateRobot.setPortrait(mpUser.getHeadImgUrl());
                inputCreateRobot.setCallback(mRobotCallback);
                inputCreateRobot.setOwner("admin");
                IMResult<OutputCreateRobot> imResult = UserAdmin.createRobot(inputCreateRobot);
                if (imResult != null && imResult.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {

                }
            }
        } catch (WxErrorException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String getRobotId(String wxId) {
        return mShadowPrefix + wxId;
    }

    private String getWXId(String robotId) {
        if (robotId.startsWith(mShadowPrefix)) {
            return robotId.substring(3);
        }
        return null;
    }

    //????????????????????????????????????
    private void sendWXTextMsg(String wxId, String msg) {
        WxMpKefuMessage message = WxMpKefuMessage.TEXT().toUser(wxId).content(msg).build();
        try {
            wxService.getKefuService().sendKefuMessage(message);
        } catch (WxErrorException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onReceiveMessage(SendMessageData messageData) {
        if (messageData.getConv().getType() != ProtoConstants.ConversationType.ConversationType_Private) {
            return;
        }
        String robotId = messageData.getConv().getTarget();
        String wxId = getWXId(robotId);

        if (wxId == null) {
            return;
        }

        WxMpKefuMessage message = WxMpKefuMessage.TEXT().toUser(wxId).content(messageData.getPayload().getSearchableContent()).build();
        try {
            wxService.getKefuService().sendKefuMessage(message);
        } catch (WxErrorException e) {
            e.printStackTrace();
        }

        return;
    }

    private boolean sendMessage(String fromUser, String toUser, MessagePayload payload) {
        Conversation conversation = new Conversation();
        conversation.setType(ProtoConstants.ConversationType.ConversationType_Private);
        conversation.setTarget(toUser);

        try {
            IMResult<SendMessageResult> result = MessageAdmin.sendMessage(fromUser, conversation, payload);
            if (result != null) {
                if (result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS) {
                    LOG.info("Send response success");
                    return true;
                } else {
                    LOG.error("Send response error {}", result.getCode());
                }
            } else {
                LOG.error("Send response is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Send response execption");
        }
        return false;
    }
}
