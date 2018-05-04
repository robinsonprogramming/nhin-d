/* 
Copyright (c) 2010, NHIN Direct Project
All rights reserved.

Authors:
   Greg Meyer      gm2552@cerner.com
 
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
in the documentation and/or other materials provided with the distribution.  Neither the name of the The NHIN Direct Project (nhindirect.org). 
nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS 
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.nhindirect.stagent.cryptography.activekeyops;

import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.mail.smime.SMIMEEnveloped;

/**
 * Factory class for creating instances of the concrete DefaultDirectRecipientInformation class.
 * @author Greg Meyer
 * @since 2.1
 */
public class DefaultDirectRecipientInformationFactory implements DirectRecipientInformationFactory
{
	protected final String encProvider;
	
	/**
	 * Constructor.  Defaults the JCE provider to the
	 *  CryptoExtensions.getJCEProviderName() value.
	 */
	public DefaultDirectRecipientInformationFactory()
	{
		this("");
	}
	
	/**
	 * Constructor
	 * @param encProvider  The name of the JCE provider used to perform decryption operations. If this value
	 * is null of empty, the CryptoExtensions.getJCEProviderName() value is used.
	 */
	public DefaultDirectRecipientInformationFactory(String encProvider)
	{
		this.encProvider = encProvider;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DirectRecipientInformation createInstance(RecipientInformation recipient, SMIMEEnveloped env) 
	{
		if (recipient == null)
			return null;
		
		return new DefaultDirectRecipientInformation(recipient, encProvider);
	}
	
	
}
