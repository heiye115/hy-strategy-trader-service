package com.hy.common.service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
//@Service
public class WXPusherService {

//    //@Value("${wxpusher.app-token}")
//    private String appToken;
//
//    //@Value("${wxpusher.my-uid}")
//    private String myUid;
//
//    public void wxMyPushHtml(String summary, String content) {
//        wxPusherHtmlOne(summary, content, myUid);
//    }
//
//    public void wxMyPushMd(String summary, String content) {
//        wxPusherMdOne(summary, content, myUid);
//    }
//
//    public void wxPusherTextOne(String summary, String content, String uid) {
//        wxPusherText(summary, content, new HashSet<>(Collections.singleton(uid)));
//    }
//
//    public void wxPusherHtmlOne(String summary, String content, String uid) {
//        wxPusherHtml(summary, content, new HashSet<>(Collections.singleton(uid)));
//    }
//
//    public void wxPusherMdOne(String summary, String content, String uid) {
//        wxPusherMd(summary, content, new HashSet<>(Collections.singleton(uid)));
//    }
//
//    public void wxPusherText(String summary, String content, Set<String> uids) {
//        wxPusher(appToken, summary, Message.CONTENT_TYPE_TEXT, content, uids);
//    }
//
//    public void wxPusherHtml(String summary, String content, Set<String> uids) {
//        wxPusher(appToken, summary, Message.CONTENT_TYPE_HTML, content, uids);
//    }
//
//    public void wxPusherMd(String summary, String content, Set<String> uids) {
//        wxPusher(appToken, summary, Message.CONTENT_TYPE_MD, content, uids);
//    }
//
//    public void wxPusher(String appToken, String summary, Integer contentType, String content, Set<String> uids) {
//        try {
//            Message message = new Message();
//            message.setAppToken(appToken);
//            message.setContentType(contentType);
//            message.setContent(content);
//            message.setUids(uids);
//            message.setSummary(summary);
//            //message.setUrl("http://wxpuser.zjiecode.com");//可选参数
//            Result<List<MessageResult>> result = WxPusher.send(message);
//            log.info("wxPusher发送结果:{}", JsonUtil.toJson(result));
//        } catch (Exception e) {
//            log.error("wxPusher报错:", e);
//        }
//    }
}
