package server;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import security.*;

import java.math.BigInteger;
import java.net.*;
import java.io.*;

/**
 * 实现了基于UDP的“服务器-客户端”通信，通信内容加密<br>
 * @author Caitao Zhan (caitaozhan@163.com)
 * @see <a href="https://github.com/caitaozhan/UDPChatAppEncryt">Github</a>
 */
class CommunicationServer extends JFrame implements Runnable
{
	private static final long serialVersionUID = -2346534561072742542L;
	private JLabel myLabel;
	private TextArea textArea;
	private JTextField jTextFieldInput;
	private JPanel panelNorth;
	private JButton rButton;   // 产生 R
	private JButton keyButton; // 产生共享 key
	private int p;             // 素数
	private int g;             // p的原根
	private int random;        // 保密的随机数
	private String r1;         // 对方的R1（假设我是Bob）
	private String sharedKey;  // 双方共享的密钥
	Thread s;
	private DatagramSocket datagramSocket;            // 用于收发UDP数据报
	private DatagramPacket sendPacket, receivePacket; // 包含具体的要传输的信息
	// 为了发送数据，要将数据封装到DatagramPacket中，使用DatagramSocket发送该包
	private SocketAddress sendAddress;
	private String name;
	private boolean canSend;
	MD5 md5;

	public CommunicationServer()
	{
		enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		try
		{
			jbInit();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void jbInit() throws Exception
	{
		myLabel = new JLabel("通信记录");
		panelNorth = new JPanel();
		panelNorth.add(myLabel);
		panelNorth.add(getRButton());
		panelNorth.add(getKeyButton());
		jTextFieldInput = new JTextField();
		jTextFieldInput.setEditable(false);
		textArea = new TextArea();
		textArea.setEditable(false);
		canSend = false;
		p = 97;
		g = 5;
		md5 = new MD5("");
		do
		{
			random = (int) (Math.random() * p);
		} while (random <= 1);

		setBounds(400, 100, 500, 500);
		setTitle("UDPServer-詹才韬");
		add(panelNorth, BorderLayout.NORTH);
		add(textArea, BorderLayout.CENTER);
		add(jTextFieldInput, BorderLayout.SOUTH);
		jTextFieldInput.addActionListener((ActionEvent e) ->
		{
			jTextFieldInput_actionPerformed(e);
		}); // 使用lambda替换匿名类

		try
		{
			datagramSocket = new DatagramSocket(8002);  // 创建接收方的套接字，IP(chosen by the kernal), 端口号8002
		}
		catch (SocketException e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		s = new Thread(this); // 创建线程
		s.start();
	}

	public void run()
	{
		while (true)
		{
			try
			{
				byte buf[] = new byte[1024];
				receivePacket = new DatagramPacket(buf, buf.length); // 可以不是每一次都new么？用同一个
				datagramSocket.receive(receivePacket);               // 通过套接字，等待接受数据
				canSend = true;                                      // 必须先受到客户端的消息，我方（服务器）才能够发送消息（给客户端）
				sendAddress = receivePacket.getSocketAddress();

				byte[] databyte_0 = receivePacket.getData();         // databyte_0 = 真正的数据 + 0...0 (0填空)
				int dataLength = receivePacket.getLength();          // 真正的数据的长度

				if (jTextFieldInput.isEditable() == true)            // 当jTextFieldInput可以编辑的时候，可以发送信息，此时才进行加密
				{
					byte[] databyte = new byte[dataLength];          // 把 databyte_0 后面的 0 去掉
					ByteArrayUtil.copyByteArray(databyte, databyte_0, dataLength);

					byte[] md5Byte = new byte[32];                   // md5 的长度是定长，32个字节
					byte[] encryptByte = new byte[databyte.length - 32];
					ByteArrayUtil.seperate(databyte, md5Byte, encryptByte);  // 拆分

					databyte = DES.decrypt(encryptByte, sharedKey);          // 解密：密文 --> 明文

					md5.updateInstance(new String(databyte));
					String md5_2 = md5.getMD5();                             // MD5：明文 --> MD5验证码
					byte[] md5Byte2 = md5_2.getBytes();
					
					if (ByteArrayUtil.equal(md5Byte, md5Byte2) == false)     // 判断拆分得到的md5 和 解密得到的明文的md5 是否一致，然后做相关处理
					{
						throw new MD5Exception("MD5验证码不相等，完整性检测失败！可能被黑客篡改");
					}
					
					String md5String = new String(md5Byte);
					String receivedString = new String(databyte);
					textArea.append("\n完整性检测成功，MD5验证码 = " + md5String);
					textArea.append("\n客户端密文是：" + new String(encryptByte));
					textArea.append("\n客户端明文是：" + receivedString + "\n");
				}
				if (jTextFieldInput.isEditable() == false)           // 当jTextFieldInput无法编辑的时候(初始阶段), 接受的是共享密钥
				{
					textArea.append("https://github.com/caitaozhan/UDPChatAppEncryt");
					name = receivePacket.getAddress().toString().trim();
					textArea.append("\n来自主机:" + name + " 端口:" + receivePacket.getPort());

					r1 = new String(databyte_0);
					r1 = r1.trim();
					textArea.append("\n客户端的R1 = " + r1);
				}
			}
			catch (IOException ioe)
			{
				textArea.append("网络通信出现错误,问题在于" + ioe.toString());
			}
			catch (MD5Exception md5e)
			{
				textArea.append(md5.toString());
			}
			catch (Exception e)
			{
				textArea.append("解密出现异常");
			}
		}
	}

	protected void processWindowEvent(WindowEvent e)
	{
		super.processWindowEvent(e);
		if (e.getID() == WindowEvent.WINDOW_CLOSING)
		{
			System.exit(0);
		}
	}

	void jTextFieldInput_actionPerformed(ActionEvent e)
	{
		try
		{
			if (canSend == true) // 必须先等待客户端先发送消息
			{
				textArea.append("\n服务端:");
				String sendString = jTextFieldInput.getText().trim();
				textArea.append(sendString + "\n");
				byte[] databyte = sendString.getBytes();

				md5.updateInstance(sendString);
				String MD5str = md5.getMD5();
				byte[] md5Byte = MD5str.getBytes();                // MD5报文鉴别码

				databyte = DES.encrypt(databyte, sharedKey);          // 生成密文

				databyte = ByteArrayUtil.combine(md5Byte, databyte);  // 把MD5和密文一起发过去

				sendPacket = new DatagramPacket(databyte, databyte.length, sendAddress);
				datagramSocket.send(sendPacket);

				jTextFieldInput.setText("");
				canSend = false;             // 恢复为“不能发送”的状态，等待客户端发送下一个消息
			}
			else
			{
				System.out.println("不知道发送给谁");
			}
		}
		catch (IOException ioe)
		{
			textArea.append("网络通信出现错误，问题在于" + e.toString());
		}
		catch (Exception exception)
		{
			exception.printStackTrace();
		}
	}

	public JButton getRButton()
	{
		if (rButton == null) // 当第一次调用这个方法的时候，rButton == null，进行初始化操作
		{
			rButton = new JButton("产生发送R2");
			rButton.addActionListener((ActionEvent e) ->
			{
				try
				{
					if (canSend == true)
					{
						String G = String.valueOf(g);
						String R2 = modularExponentiation(G); // 假设我是Bob，产生 R2
						textArea.append("\n服务端的R2 = " + R2);
						byte[] databyte = R2.getBytes();

						sendPacket = new DatagramPacket(databyte, databyte.length, sendAddress);
						datagramSocket.send(sendPacket); // 发送 R2

						canSend = false;                 // 恢复为“不能发送”的状态，等待客户端发送下一个消息
						rButton.setVisible(false);       // “产生R”的按钮消失
						keyButton.setVisible(true);      // “产生共享key”的按钮出现
					}
					else
					{
						System.out.println("不知道发送给谁");
					}
				}
				catch (IOException ioe)
				{
					textArea.append("网络通信出现错误，问题在于" + e.toString());
				}
				catch (Exception exception)
				{
					exception.printStackTrace();
				}
			});
		}
		return rButton;
	}

	public JButton getKeyButton()
	{
		if (keyButton == null) // 当第一次调用这个方法的时候，keyButton == null，进行初始化操作
		{
			keyButton = new JButton("产生共享密钥");
			keyButton.setVisible(false);
			keyButton.addActionListener((ActionEvent e) ->
			{
				try
				{
					String key = modularExponentiation(r1);      // 产生sharedKey（不是8位）
					sharedKey = NormalizeToEight.normalize(key); // 规格化成为8位的sharedKey
					textArea.append("\n共享密钥是: " + sharedKey + "\n产生共享密钥使用了 Diffie-Hellman-Caitao算法\n");
					textArea.append("使用了MD5算法进行完整性检测，加密解密算法使用了 DES算法\n-----------请放心，以下通信是经过加密的！------------\n");
					canSend = false;                             // 恢复为“不能发送”的状态，等待客户端发送下一个消息
					keyButton.setVisible(false);                 // “产生共享key”按钮消失
					jTextFieldInput.setEditable(true);           // 输入栏可以编辑
				}
				catch (NumberFormatException nfe)
				{
					textArea.append("String 转换 int 异常");
				}
				catch (Exception exception)
				{
					exception.printStackTrace();
				}
			});
		}
		return keyButton;
	}

	/**
	 * 模幂运算
	 * 
	 * @param base 底数
	 * @return 模幂运算结果
	 * 
	 */
	public String modularExponentiation(String base)
	{
		BigInteger tmp = new BigInteger(base);       // tmp = base
		tmp = tmp.pow(random);                       // tmp = base^random
		tmp = tmp.mod(BigInteger.valueOf(p));        // tmp = tmp%p
		return tmp.toString();
	}

}

public class UDPCommunicationServer
{
	public static void main(String[] args)
	{
		CommunicationServer UDPserver = new CommunicationServer();
		UDPserver.setVisible(true);
	}
}
