package org.librazy.demo.dubbo.service;

import org.librazy.demo.dubbo.config.RedisUtils;
import org.librazy.demo.dubbo.domain.SrpAccountEntity;
import org.librazy.demo.dubbo.model.SrpSignupForm;
import com.alibaba.dubbo.config.annotation.Service;
import com.bitbucket.thinbus.srp6.js.SRP6JavascriptServerSession;
import com.bitbucket.thinbus.srp6.js.SRP6JavascriptServerSessionSHA256;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambdaworks.redis.SetArgs;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Base64;

@SuppressWarnings("unchecked")
@Service
@Component
public class SrpSessionServiceImpl implements SrpSessionService {

    private final ObjectMapper mapper;

    private final StatefulRedisConnection<String, String> connection;

    private final UserSessionService userSessionService;

    private SRP6JavascriptServerSession session;

    private Long id;

    @Autowired
    public SrpSessionServiceImpl(ObjectMapper mapper, StatefulRedisConnection<String, String> connection, UserSessionService userSessionService) {
        this.mapper = mapper;
        this.connection = connection;
        this.userSessionService = userSessionService;
    }

    @Override
    public void newSession(String N, String g) {
        session = new SRP6JavascriptServerSessionSHA256(N, g);
    }

    @Override
    public void loadSession(long id) {
        this.id = id;
        String b64os = connection.sync().get(RedisUtils.SrpSession(String.valueOf(id)));
        if (b64os == null) throw new IllegalStateException();
        try {
            byte[] bs = Base64.getDecoder().decode(b64os);
            ByteArrayInputStream bais = new ByteArrayInputStream(bs);
            ObjectInputStream ois = new ObjectInputStream(bais);
            session = (SRP6JavascriptServerSession) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String step1(SrpAccountEntity account) {
        id = account.getId();
        String b = session.step1(account.getUser().getEmail(), account.getSalt(),
                account.getVerifier());
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(session);
            String b64os = Base64.getEncoder().encodeToString(baos.toByteArray());
            String result = connection.sync().set(RedisUtils.SrpSession(String.valueOf(id)), b64os, SetArgs.Builder.ex(10).nx());
            if (!result.equals("OK")) {
                throw new RuntimeException("session already exists");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return b;
    }

    @Override
    public String step2(String A, String M1, String ua) throws Exception {
        try {
            String m2 = session.step2(A, M1);
            String sid = session.getSessionKey(true);
            String key = session.getSessionKey(false);
            userSessionService.newSession(String.valueOf(id), sid, ua, key);
            return m2;
        } finally {
            connection.sync().del(RedisUtils.SrpSession(String.valueOf(id)));
        }
    }

    @Override
    public String getSessionKey(boolean doHash) {
        if (session == null) {
            throw new UnsupportedOperationException("session not created/restored yet");
        }
        return session.getSessionKey(doHash);
    }

    @Override
    public void saveSignup(SrpSignupForm signupForm) {
        if (id >= 0) throw new RuntimeException("Trying to run signup action with positive id:" + id);
        try {
            if (!"OK".equals(
                    connection.sync().set(RedisUtils.SignupSession(String.valueOf(id)), mapper.writeValueAsString(signupForm), SetArgs.Builder.ex(10).nx())
            )) {
                throw new RuntimeException("signup form already exists");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SrpSignupForm getSignup() {
        if (id >= 0) throw new RuntimeException("Trying to run signup action with positive id:" + id);
        try {
            String json = connection.sync().get(RedisUtils.SignupSession(String.valueOf(id)));
            return mapper.readerFor(SrpSignupForm.class).readValue(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void confirmSignup(long now) {
        String sid = session.getSessionKey(true);
        if (id >= 0) throw new RuntimeException("Trying to run signup action with positive id:" + id);
        if (!userSessionService.renameId(String.valueOf(id), sid, String.valueOf(now)))
            throw new RuntimeException("id already exists:" + now);
    }
}