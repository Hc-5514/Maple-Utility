package com.maple.utility.exception;

import org.springframework.http.HttpStatus;

public class NexonApiException extends ApiException {

	private final String nexonErrorName;
	private final String nexonErrorMessage;

	public NexonApiException(
			HttpStatus status,
			String code,
			String message,
			String nexonErrorName,
			String nexonErrorMessage
	) {
		super(status, code, message);
		this.nexonErrorName = nexonErrorName;
		this.nexonErrorMessage = nexonErrorMessage;
	}

	public String getNexonErrorName() {
		return nexonErrorName;
	}

	public String getNexonErrorMessage() {
		return nexonErrorMessage;
	}
}
