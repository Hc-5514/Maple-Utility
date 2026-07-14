package com.maple.utility.security;

import com.maple.utility.entity.OAuthProvider;

public interface OAuthClient {

	OAuthProvider provider();

	OAuthUserInfo getUserInfo(String code);
}
