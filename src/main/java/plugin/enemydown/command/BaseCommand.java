package plugin.enemydown.command;

import java.io.IOException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *コマンド実行して動かすプラグイン処理の基底クラスです
 */
public abstract class BaseCommand implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label,
      String[] args) {
    try {
      if (sender instanceof Player player) {
        return onExecutePlayerCommand(player, command, label, args);
      } else {
        return onExecuteNPCCommand(sender, command, label, args);
      }
    } catch (IOException e) {
      //IOExceptionが発生した場合の処理を行う
      e.printStackTrace();
      return false;
    }
  }


  /**
   * コマンド実行者がプレイヤーだった場合に実行します
   *
   * @param player　コマンドを実行したプレイヤー
   * @param command コマンド
   * @param label ラベル
   * @param args コマンド引数
   * @return 処理の実行有無
   */
  public abstract boolean onExecutePlayerCommand(Player player, Command command, String label,
      String[] args) throws IOException;

  /**
   * コマンド実行者がプレイヤー以外だった場合に実行します
   *
   * @param sender　コマンド実行者
   * * @param command コマンド
   * * @param label ラベル
   * * @param args コマンド引数
   * @return 処理の実行有無
   */
  public abstract boolean onExecuteNPCCommand(CommandSender sender, Command command, String label,
      String[] args);
}