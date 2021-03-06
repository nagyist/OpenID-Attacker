/*
 * OpenID Attacker
 * (C) 2015 Christian Mainka & Christian Koßmann
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package wsattacker.sso.openid.attacker.evaluation.attack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import wsattacker.sso.openid.attacker.attack.parameter.AttackParameterKeeper;
import wsattacker.sso.openid.attacker.config.OpenIdServerConfiguration;
import wsattacker.sso.openid.attacker.controller.ServerController;
import wsattacker.sso.openid.attacker.evaluation.attack.AttackResult.Result;
import wsattacker.sso.openid.attacker.evaluation.LoginResult;
import wsattacker.sso.openid.attacker.evaluation.ServiceProvider;

public abstract class AbstractAttack {
    protected ServerController serverController;
    protected AttackParameterKeeper keeper;
    protected final ServiceProvider serviceProvider;
    
    public AbstractAttack(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        serverController = new ServerController();
    }
    
    protected boolean isSignatureValid(LoginResult loginResult) {
        //Map<String, String> authenticationResponseMap = new LinkedHashMap<>();
        
        String authenticationResponse = loginResult.getLogEntryOfToken().getResponse();
        String[] authenticationResponseLines = authenticationResponse.split("\n");
        
        String signatureFromToken = "";
        
        for (String authenticationResponseLine: authenticationResponseLines) {
            if (authenticationResponseLine.startsWith("openid.sig:")) {
                String[] lineSeparatedByColon = authenticationResponseLine.split(":");
                
                if (lineSeparatedByColon.length == 2) {
                    signatureFromToken = lineSeparatedByColon[1].trim();
                }
            }
        
        }
        
        String attackSignatureValue = keeper.getParameter("openid.sig").getAttackValue();
        
        if (signatureFromToken.equals(attackSignatureValue)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * This method is executed BEFORE the attack to initialize important
     * variables and reset ALL attack parameters.
     * 
     * Custom preliminary work can be performed by overwriting this method.
     */
    protected void beforeAttack() {
        keeper = serverController.getServer().getParameterConfiguration();
        keeper.resetAllParameters();
    }
    
    /**
     * This method is executed AFTER to perform cleanup.
     * 
     * Custom cleanup can be performed by overwriting this method.
     */
    protected void afterAttack() {
        keeper.resetAllParameters();
        
        OpenIdServerConfiguration.getAttackerInstance().setPerformAttack(false);
        OpenIdServerConfiguration.getAnalyzerInstance().setPerformAttack(false);
    }
    
    /**
     * Wraps the method performAttacks() into beforeAttack() and
 afterAttack().
     * 
     * @return A list of the attack results.
     */
    public List<AttackResult> performAttacks() {
        
        
        List<AttackResult> attackResults = new ArrayList<>();
        
        List<Method> attackMethods = new ArrayList<>();
        
        for (Method method: this.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Attack.class)) {
                
                attackMethods.add(method);
            }
        }
        
        attackMethods.sort((Method m1, Method m2) -> {
            int numberOfM1 = m1.getAnnotation(Attack.class).number();
            int numberOfM2 = m2.getAnnotation(Attack.class).number();
            
            if (numberOfM1 < numberOfM2) {
                return -1;
            } else if (numberOfM1 > numberOfM2) {
                return 1;
            }
            
            return 0;
        });
        
        for (Method method: attackMethods) {
            int dependsOnFailureOf = method.getAnnotation(Attack.class).dependsOnFailureOf();
            if (dependsOnFailureOf != -1) {
                if (dependsOnFailureOf >= attackResults.size()) {
                    continue;
                }
                
                AttackResult attackResultOfDependency = attackResults.get(dependsOnFailureOf);
                if (attackResultOfDependency.getResult() != Result.FAILURE) {
                    continue;
                }
            }
            try {
                
                beforeAttack();
                method.setAccessible(true);
                attackResults.add((AttackResult) method.invoke(this));
                method.setAccessible(false);
                afterAttack();
            } catch (IllegalAccessException ex) {
                Logger.getLogger(AbstractAttack.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(AbstractAttack.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvocationTargetException ex) {
                Logger.getLogger(AbstractAttack.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return attackResults;
    }
}