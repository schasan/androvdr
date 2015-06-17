## Generel ##

---

AndroVDR is an application for [Android](http://www.android.com) (from version 1.6) to control [VDR](http://tvdr.de) by Klaus Schmidinger.<br>
It is based on <a href='http://www.jollina.de/misc/vdrControlHelp.htm'>VDRControl</a> and was renamed for publication in <a href='http://market.android.com/details?id=de.androvdr'>Market</a> into AndroVDR.<br>
<br>
<h2>SVDRP</h2>
<hr />
The communication with VDR occurs via SVDRP (Simple VDR Protocol). To be able to use it, the device has to be activated in VDR. You can do this in the configuration file <i>svdrphosts.conf</i>.<br>
The VDR does not allow multiple connections from different clients, so the connection gets established on demand and disconnects after 10 seconds without usage. As this interface may be used by other devices or plugins, temporary connection problems may occur.<br>
<br>
<h2>Remote Control</h2>
<hr />
The buttons <i>Schedule</i>, <i>Timer</i>, <i>Channels</i> and <i>Records</i> open the according listview on normal push. A long push displays the corresponding view in VDR.<br>
Also, the <i>Power On</i> button has two functions. The normal push sends a WOL-Broadcast into the local network, the long push into the internet. The requirement for this is that the router forwards the UDP-port 9 onto the broadcast-address of the local network (e.g. 192.168.1.255). If the router has a static ARP-table you can also add the according entry and the forwarding onto the IP-address of the VDR.<br>
<br>
<h2>Control via internet</h2>
<hr />
The encrypted connection occurs via a SSH-tunnel.<br>
<br>
Requirements:<br>
<ul><li>SSH-server on the VDR<br>
</li><li>the router must have a public (static or e.g. DynDNS) address<br>
</li><li>a port forwarded onto the SSH-server on the VDR</li></ul>

For authentication via SSH-key, the key for the VDR needs to be imported. The key has to be copied into the root directory of the SD-Card and should be deleted after the import.<br>
<br>
The connection has to be created and closed manually via the menu item <i>Forwarding</i>. After the start of the SSH-client, if the remote station is unknow, the client checks the fingerprint and asks after their confirmation, if necessary, for a password or passphrase for the key.<br>
<br>
<h2>Channel logos</h2>
<hr />
Logos can be displayed in the channel list, but are not part of the program for copyright reasons.<br>
<br>
Requirements:<br>
<ul><li>Logos e.g. from <a href='http://copperhead.htpc-forum.de/channellogos.php'>http://copperhead.htpc-forum.de/channellogos.php</a>
</li><li>PNG<br>
</li><li>the name has to be equal with the channels name<br>
</li><li>saved on the SD-Card in the folder <i>AndroVDR/logos</i>
</li><li>activate <i>Senderlogos</i> in the configuration</li></ul>

<h2>Gestures</h2>
<hr />
Pre-defined commands can be transmitted to the VDR by self-created gestures. The name of the gesture has to be equal with the ones from below.<br>
<br>
<table><thead><th> <b>Name</b> </th><th> <b>Function</b> </th></thead><tbody>
<tr><td> power       </td><td> Power off       </td></tr>
<tr><td> up, down, left, right </td><td> Arrowkey        </td></tr>
<tr><td> ok          </td><td> OK-button       </td></tr>
<tr><td> back        </td><td> back            </td></tr>
<tr><td> 0-9         </td><td> number key      </td></tr>
<tr><td> red, green, yellow, blue </td><td> color key       </td></tr>
<tr><td> chan_up, chan_down, prev_chan </td><td> channel selection </td></tr>
<tr><td> vol_up, vol_down, mute </td><td> volume          </td></tr>
<tr><td> play, pause, stop </td><td> play            </td></tr>
<tr><td> record      </td><td> start record    </td></tr>
<tr><td> next, prev, fastfwd, fastrew </td><td> spooling        </td></tr>
<tr><td> menu        </td><td> OSD menu        </td></tr>
<tr><td> info        </td><td> OSD info        </td></tr>
<tr><td> schedule    </td><td> OSD program     </td></tr>
<tr><td> channels    </td><td> OSD channel     </td></tr>
<tr><td> timers      </td><td> OSD timer       </td></tr>
<tr><td> recordings  </td><td> OSD records     </td></tr>
<tr><td> setup       </td><td> OSD setup       </td></tr>
<tr><td> commands    </td><td> OSD commands    </td></tr>
<tr><td> audio       </td><td> OSD audio       </td></tr>
<tr><td> subtitles   </td><td> OSD subtitle    </td></tr>
<tr><td> user1 - user9 </td><td> user-defined    </td></tr></tbody></table>

<h2>User-defined layout</h2>
<hr />
The configuration file name has to be <i>mytab</i> and be saved in the folder <i>AndroVDR</i> of the SD-Card. It is a simple text file, in which every line represents a button. The statements are separated by commas.<br>
<br>
Format:<br>
<ul><li>Type<br>
<ul><li>Text<br>
</li><li>Image<br>
</li></ul></li><li>label or if the type is an image the relative folder/filename<br>
</li><li>offset from the left border<br>
</li><li>offset from the upper border<br>
</li><li>height<br>
</li><li>width<br>
</li><li>action<br>
<ul><li>see Gestures</li></ul></li></ul>

The units for offset, width and height are in pixels. A <i>#</i> at the beginning of a line marks a comment.<br>
<br>
Example:<br>
<pre><code>Text,Menu,25,25,60,60,menu<br>
Text,Back,90,25,60,60,ok<br>
Text,Sched,90,140,60,60,schedule<br>
Image,images/chan.png,61,81,60,70,channels<br>
</code></pre>

<h2>Logging</h2>
<hr />
If necessary, the output of log-information can be activated. Possible options:<br>
<ul><li>System<br>
<ul><li>read it with <i>adb logcat</i> from Android-SDK<br>
</li></ul></li><li>SDCard<br>
<ul><li>output occurs into the file <i>log.txt</i> in the folder <i>AndroVDR</i> on the SD-Card. This file will be created on every start of the program.<br>
</li></ul></li><li>SDCard All<br>
<ul><li>Like SDCard, but does not delete the log file.<br>
Attention, the file may become very large.</li></ul></li></ul>

<h2>Plugins (for developer)</h2>
<hr />
It is possible to extend AndroVDR into a universal remote control with the existing Plugin-Interface. Any device can be controlled, if the device provides a network-interface. The plugin only has to implement the interface <a href='http://code.google.com/p/androvdr/source/browse/src/de/androvdr/devices/IActuator.java'>IActuator</a> (or <a href='http://code.google.com/p/androvdr/source/browse/src/de/androvdr/devices/IDevice.java'>IDevice</a>). The provided functions can then be used for personal layouts. With free definable macros it is possible to implement complex scenarios at the push of a button.<br>
<br>
Implementation:<br>
<ul><li>VdrDevice<br>
</li><li>ActivityDevice<br>
</li><li>Macros</li></ul>

Realisation:<br>
<ul><li>Onkyo amplifier<br>
</li><li><a href='http://www.ezcontrol.de'>EZControl</a>
</li><li><a href='http://www.irtrans.de'>IRTrans</a></li></ul>

<h2>Used Libraries</h2>
<hr />
<ul><li><a href='http://acra.googlecode.com'>ACRA</a>
</li><li><a href='http://www.jcraft.com/jsch'>JSch</a>
</li><li><a href='http://www.slf4j.org'>slf4j</a>
</li><li><a href='http://developer.berlios.de/projects/lazybones'>svdrp4l</a>